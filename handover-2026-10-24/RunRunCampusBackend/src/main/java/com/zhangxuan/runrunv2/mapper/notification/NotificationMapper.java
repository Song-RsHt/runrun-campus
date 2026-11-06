package com.zhangxuan.runrunv2.mapper.notification;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/** 站内通知及订阅投递数据访问；使用调用方事务绑定的 JDBC 连接，不开启或提交事务。 */
@Repository
public class NotificationMapper {

    private final JdbcTemplate jdbc;

    public NotificationMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> findUserNotifications(long userId) {
        return jdbc.queryForList(
                """
                SELECT id,
                    order_id,
                    type,
                    content,
                    created_at,
                    read_at
                FROM runrun_v2.user_notifications
                WHERE user_id = ?
                ORDER BY created_at DESC,
                    id DESC
                LIMIT 100
                """,
                userId);
    }

    public int markNotificationRead(long notificationId, long userId) {
        return jdbc.update(
                """
                UPDATE runrun_v2.user_notifications
                SET read_at = COALESCE(read_at, now())
                WHERE id = ?
                    AND user_id = ?
                """,
                notificationId,
                userId);
    }

    public List<Map<String, Object>> claimOutboxDeliveries(int limit) {
        return jdbc.queryForList(
                """
                SELECT *
                FROM runrun_v2.claim_notification_outbox(?)
                """,
                limit);
    }

    public Object completeDelivery(long id, boolean success, String error) {
        return jdbc.queryForObject(
                """
                SELECT runrun_v2.complete_notification_outbox(?, ?, ?)
                """,
                Object.class,
                id,
                success,
                error);
    }
}
