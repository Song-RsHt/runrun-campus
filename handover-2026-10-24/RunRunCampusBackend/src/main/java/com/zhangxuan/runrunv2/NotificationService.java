package com.zhangxuan.runrunv2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable v2 notification facade. Business callers use enqueue in their own transaction. */
@Service
public class NotificationService {
    public record Notification(long id, Long orderId, String type, Map<String,Object> content,
                               Instant createdAt, Instant readAt) {}
    public record DeliveryResult(boolean sent, String error) {}
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final WechatSubscriptionGateway wechat;

    public NotificationService(JdbcTemplate db, ObjectMapper json, WechatSubscriptionGateway wechat) {
        this.db = db; this.json = json; this.wechat = wechat;
    }

    @Transactional
    public long enqueue(long recipientId, Long orderId, String type, String eventKey,
                        Map<String,Object> content, Map<String,Object> wechatPayload) {
        if (eventKey == null || eventKey.isBlank() || content == null) throw IdentityFailure.invalid();
        try {
            String body = json.writeValueAsString(content);
            String wx = json.writeValueAsString(wechatPayload == null ? Map.of() : wechatPayload);
            Long id = db.queryForObject("SELECT runrun_v2.enqueue_notification(?,?,?,?,?::jsonb,?::jsonb)", Long.class,
                    recipientId, orderId, type, eventKey, body, wx);
            return id == null ? 0L : id;
        } catch (JsonProcessingException e) { throw IdentityFailure.invalid(); }
    }

    public List<Notification> list(long userId, int limit) {
        int bounded = Math.max(1, Math.min(limit, 100));
        return db.query("SELECT id,order_id,type,content,created_at,read_at FROM runrun_v2.user_notifications "
                + "WHERE user_id=? ORDER BY created_at DESC,id DESC LIMIT ?", (r,n) -> {
            try {
                @SuppressWarnings("unchecked") Map<String,Object> content = json.readValue(r.getString("content"), Map.class);
                return new Notification(r.getLong("id"), (Long) r.getObject("order_id"), r.getString("type"), content,
                        r.getTimestamp("created_at").toInstant(), r.getTimestamp("read_at") == null ? null : r.getTimestamp("read_at").toInstant());
            } catch (Exception e) { throw new IllegalStateException("notification content unavailable"); }
        }, userId, bounded);
    }

    @Transactional
    public void markRead(long userId, long id) {
        db.update("UPDATE runrun_v2.user_notifications SET read_at=COALESCE(read_at,now()) WHERE id=? AND user_id=?", id, userId);
    }

    /** Worker entrypoint. Claim and completion are separate transactions so a failed send never rolls back business data. */
    @Transactional
    public int deliverBatch(int limit) {
        List<Map<String,Object>> rows = db.queryForList("SELECT * FROM runrun_v2.claim_notification_outbox(?)", limit);
        for (Map<String,Object> row : rows) {
            long id = ((Number) row.get("id")).longValue();
            boolean ok = false; String error;
            if ("IN_APP".equals(row.get("channel"))) {
                ok = true; error = null;
            } else {
                try {
                    @SuppressWarnings("unchecked") Map<String,Object> payload = json.readValue(String.valueOf(row.get("payload")), Map.class);
                    DeliveryResult result = wechat.send(((Number) row.get("recipient_id")).longValue(), payload);
                    ok = result != null && result.sent();
                    error = ok ? null : (result == null ? "WECHAT_SEND_FAILED" : result.error());
                } catch (Exception e) { error = "WECHAT_SEND_FAILED"; }
            }
            db.queryForObject("SELECT runrun_v2.complete_notification_outbox(?,?,?)", Object.class, id, ok, error == null ? null : sanitize(error));
        }
        return rows.size();
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) return "DELIVERY_FAILED";
        return value.replaceAll("[^A-Za-z0-9_.:-]", "_").substring(0, Math.min(500, value.length()));
    }
}



