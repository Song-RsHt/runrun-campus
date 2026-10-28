package com.zhangxuan.runrunv2;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

@Service
public class NotificationService {
    private final JdbcTemplate db;
    private final IdentityService identity;
    public NotificationService(JdbcTemplate db, IdentityService identity) { this.db = db; this.identity = identity; }

    @Transactional(readOnly = true)
    public List<Map<String,Object>> list(String token) {
        long user = Long.parseLong(identity.authenticate(token).id());
        return db.queryForList("SELECT id,order_id,type,content,created_at,read_at FROM runrun_v2.user_notifications WHERE user_id=? ORDER BY created_at DESC,id DESC LIMIT 100", user);
    }

    @Transactional
    public void markRead(String token, long id) {
        long user = Long.parseLong(identity.authenticate(token).id());
        db.update("UPDATE runrun_v2.user_notifications SET read_at=COALESCE(read_at,now()) WHERE id=? AND user_id=?", id, user);
    }

    @Transactional
    public List<Map<String,Object>> claim(int limit) {
        return db.queryForList("SELECT * FROM runrun_v2.claim_notification_outbox(?)", Math.max(1, Math.min(limit, 100)));
    }

    @Transactional
    public void complete(long id, boolean success, String error) {
        db.queryForObject("SELECT runrun_v2.complete_notification_outbox(?,?,?)", Object.class, id, success, error);
    }

    @Scheduled(fixedDelayString = "${rrc.notifications.worker-delay-ms:30000}")
    public void processDue() {
        for (Map<String,Object> item : claim(20)) {
            complete(((Number)item.get("id")).longValue(), true, null);
        }
    }
}
