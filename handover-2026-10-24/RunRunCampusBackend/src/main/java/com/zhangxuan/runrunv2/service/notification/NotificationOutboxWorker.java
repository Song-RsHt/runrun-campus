package com.zhangxuan.runrunv2.service.notification;
import com.zhangxuan.runrunv2.integration.wechat.WechatSubscriptionGateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Claims and completes in short independent transactions; network I/O is outside both. */
@Service
public class NotificationOutboxWorker {
    public record Delivery(long id, long recipientId, String eventKey, String channel,
                           Map<String,Object> payload, int attemptCount) {}
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final WechatSubscriptionGateway wechat;
    private final TransactionTemplate transactions;

    public NotificationOutboxWorker(JdbcTemplate db, ObjectMapper json, WechatSubscriptionGateway wechat,
                                    org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.db = db; this.json = json; this.wechat = wechat;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public List<Delivery> claim(int limit) {
        return transactions.execute(status -> db.query("SELECT id,recipient_id,event_key,channel,payload,attempt_count FROM runrun_v2.claim_notification_outbox(?)",
                (r,n) -> {
                    try {
                        @SuppressWarnings("unchecked") Map<String,Object> payload = json.readValue(r.getString("payload"), Map.class);
                        return new Delivery(r.getLong("id"), r.getLong("recipient_id"), r.getString("event_key"),
                                r.getString("channel"), payload, r.getInt("attempt_count"));
                    } catch (Exception e) { throw new IllegalStateException("outbox payload unavailable"); }
                }, Math.max(1, Math.min(limit, 100))));
    }

    public void complete(long id, boolean success, String error) {
        transactions.executeWithoutResult(status -> db.queryForObject("SELECT runrun_v2.complete_notification_outbox(?,?,?)", Object.class, id, success,
                sanitize(error)));
    }

    @Scheduled(fixedDelayString = "${rrc.notifications.worker-delay-ms:15000}")
    public void runOnce() {
        if (!m08Installed()) return;
        transactions.executeWithoutResult(status -> db.queryForObject("SELECT runrun_v2.run_notification_review_timers()", Integer.class));
        for (Delivery delivery : claim(50)) {
            if ("IN_APP".equals(delivery.channel())) {
                complete(delivery.id(), true, null);
                continue;
            }
            try {
                NotificationService.DeliveryResult result = wechat.send(delivery.recipientId(), delivery.payload());
                complete(delivery.id(), result != null && result.sent(), result == null ? "WECHAT_SEND_FAILED" : result.error());
            } catch (Exception e) {
                complete(delivery.id(), false, "WECHAT_SEND_FAILED");
            }
        }
    }

    private boolean m08Installed() {
        try {
            return Boolean.TRUE.equals(db.queryForObject("SELECT to_regprocedure('runrun_v2.claim_notification_outbox(integer)') IS NOT NULL AND to_regprocedure('runrun_v2.run_notification_review_timers()') IS NOT NULL", Boolean.class));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) return null;
        String clean = value.replaceAll("[^A-Za-z0-9_.:-]", "_");
        return clean.substring(0, Math.min(500, clean.length()));
    }
}
