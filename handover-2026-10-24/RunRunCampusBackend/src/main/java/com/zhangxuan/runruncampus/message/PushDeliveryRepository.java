package com.zhangxuan.runruncampus.message;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.List;

@Repository
public class PushDeliveryRepository {
    private final JdbcTemplate jdbc;

    public PushDeliveryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void enqueue(OrderPushRequested event) {
        jdbc.update("""
                insert into push_delivery_tasks
                    (recipient_id, order_id, title, content, idempotent_token, destination, status, attempts,
                     next_attempt_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, 'PENDING', 0, now(), now(), now())
                on conflict (idempotent_token) do nothing
                """, event.recipientId(), event.orderId(), event.title(), event.content(), event.idempotentToken(),
                event.destination() == null ? null : event.destination().name());
    }

    @Transactional
    public Optional<PushDeliveryTask> claimNext() {
        return jdbc.query("""
                with candidate as (
                    select id from push_delivery_tasks
                    where (status = 'PENDING' and next_attempt_at <= now())
                       or (status = 'PROCESSING' and locked_until <= now())
                    order by next_attempt_at, id
                    for update skip locked
                    limit 1
                )
                update push_delivery_tasks task
                set status = 'PROCESSING', locked_until = now() + interval '2 minutes', updated_at = now()
                from candidate where task.id = candidate.id
                returning task.id, task.recipient_id, task.order_id, task.title, task.content,
                          task.idempotent_token, task.destination, task.attempts, task.created_at
                """, (rs, row) -> new PushDeliveryTask(rs.getLong("id"),
                        new OrderPushRequested(rs.getLong("recipient_id"), rs.getObject("order_id", Long.class),
                                rs.getString("title"), rs.getString("content"), rs.getString("idempotent_token"),
                                destination(rs.getString("destination"))),
                        rs.getInt("attempts"), rs.getTimestamp("created_at").toInstant())).stream().findFirst();
    }

    public void markSent(Long id) {
        jdbc.update("""
                update push_delivery_tasks set status = 'SENT', locked_until = null, last_error = null,
                    sent_at = now(), updated_at = now() where id = ? and status = 'PROCESSING'
                """, id);
    }

    public void markFailure(Long id, int attempts, String error, Instant nextAttemptAt, boolean dead) {
        jdbc.update("""
                update push_delivery_tasks set status = ?, attempts = ?, last_error = ?, next_attempt_at = ?,
                    locked_until = null, updated_at = now() where id = ? and status = 'PROCESSING'
                """, dead ? PushDeliveryStatus.DEAD.name() : PushDeliveryStatus.PENDING.name(), attempts, error,
                Timestamp.from(nextAttemptAt), id);
    }

    public long count(PushDeliveryStatus status) {
        Long result = jdbc.queryForObject("select count(*) from push_delivery_tasks where status = ?",
                Long.class, status.name());
        return result == null ? 0 : result;
    }

    public int deleteByRecipient(Long recipientId) {
        return jdbc.update("delete from push_delivery_tasks where recipient_id = ?", recipientId);
    }

    public DeliveryPage page(PushDeliveryStatus status, int page, int size) {
        long total = count(status);
        List<DeliveryItem> items = jdbc.query("""
                select id, recipient_id, order_id, title, destination, status, attempts, next_attempt_at,
                       last_error, created_at, updated_at, sent_at
                from push_delivery_tasks where status = ?
                order by created_at desc, id desc limit ? offset ?
                """, (rs, row) -> new DeliveryItem(rs.getLong("id"), rs.getLong("recipient_id"),
                        rs.getObject("order_id", Long.class), rs.getString("title"),
                        destination(rs.getString("destination")),
                        PushDeliveryStatus.valueOf(rs.getString("status")), rs.getInt("attempts"),
                        instant(rs.getTimestamp("next_attempt_at")), rs.getString("last_error"),
                        instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at")),
                        instant(rs.getTimestamp("sent_at"))), status.name(), size, page * size);
        return new DeliveryPage(items, page, size, total, (int) Math.ceil((double) total / size));
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private PushDestination destination(String value) {
        return value == null ? null : PushDestination.valueOf(value);
    }

    public record DeliveryPage(List<DeliveryItem> items, int page, int size,
                               long totalElements, int totalPages) { }

    public record DeliveryItem(Long id, Long recipientId, Long orderId, String title, PushDestination destination,
                               PushDeliveryStatus status, int attempts, Instant nextAttemptAt,
                               String lastError, Instant createdAt, Instant updatedAt, Instant sentAt) { }
}
