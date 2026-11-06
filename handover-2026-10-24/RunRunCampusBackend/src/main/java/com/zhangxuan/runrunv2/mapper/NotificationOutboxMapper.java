package com.zhangxuan.runrunv2.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhangxuan.runrunv2.model.Delivery;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/** NotificationOutboxWorker数据访问；使用调用方事务绑定的 JDBC 连接，不开启或提交事务。 */
@Repository
public class NotificationOutboxMapper {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public NotificationOutboxMapper(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public List<Delivery> claimOutboxDeliveries(int limit) {
        return jdbc.query(
                """
                SELECT id,
                    recipient_id,
                    event_key,
                    channel,
                    payload,
                    attempt_count
                FROM runrun_v2.claim_notification_outbox(?)
                """,
                (r, n) -> {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payload =
                                json.readValue(r.getString("payload"), Map.class);
                        return new Delivery(
                                r.getLong("id"),
                                r.getLong("recipient_id"),
                                r.getString("event_key"),
                                r.getString("channel"),
                                payload,
                                r.getInt("attempt_count"));
                    } catch (Exception e) {
                        throw new IllegalStateException("outbox payload unavailable");
                    }
                },
                limit);
    }

    public Object completeDelivery(long outboxId, boolean success, String error) {
        return jdbc.queryForObject(
                """
                SELECT runrun_v2.complete_notification_outbox(?, ?, ?)
                """,
                Object.class,
                outboxId,
                success,
                error);
    }

    public Integer runReviewTimers() {
        return jdbc.queryForObject(
                """
                SELECT runrun_v2.run_notification_review_timers()
                """,
                Integer.class);
    }

    public Boolean isNotificationRuntimeInstalled() {
        return jdbc.queryForObject(
                """
                SELECT to_regprocedure('runrun_v2.claim_notification_outbox(integer)') IS NOT NULL
                    AND to_regprocedure('runrun_v2.run_notification_review_timers()') IS NOT NULL
                """,
                Boolean.class);
    }
}
