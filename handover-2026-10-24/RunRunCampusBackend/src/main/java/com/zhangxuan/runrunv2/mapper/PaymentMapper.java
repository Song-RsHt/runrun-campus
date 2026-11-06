package com.zhangxuan.runrunv2.mapper;

import com.zhangxuan.runrunv2.vo.PaymentAttemptVO;
import com.zhangxuan.runrunv2.vo.PaymentOrderVO;
import com.zhangxuan.runrunv2.vo.RefundOrderVO;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 支付回执及退款数据访问；使用调用方事务绑定的 JDBC 连接，不开启或提交事务。 */
@Repository
public class PaymentMapper {

    private final JdbcTemplate jdbc;

    public PaymentMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String insertOriginalPayment(Long orderId, long amountCents, OffsetDateTime deadline) {
        return jdbc.queryForObject(
                """
                INSERT INTO runrun_v2.payment_orders (
                    order_id,
                    purpose,
                    amount_cents,
                    deadline
                )
                VALUES (
                    ?,
                    'ORIGINAL',
                    ?,
                    ?
                )
                RETURNING id::text
                """,
                String.class,
                orderId,
                amountCents,
                deadline);
    }

    public Integer findNextAttemptNumber(Long paymentOrderId) {
        return jdbc.queryForObject(
                """
                SELECT coalesce(max(attempt_no), 0) + 1
                FROM runrun_v2.payment_attempts
                WHERE payment_order_id = ?
                """,
                Integer.class,
                paymentOrderId);
    }

    public String insertPaymentAttempt(
            Long paymentOrderId, int attemptNo, String channelReference) {
        return jdbc.queryForObject(
                """
                INSERT INTO runrun_v2.payment_attempts (
                    payment_order_id,
                    attempt_no,
                    status,
                    channel_reference
                )
                VALUES (
                    ?,
                    ?,
                    'PROCESSING',
                    ?
                )
                RETURNING id::text
                """,
                String.class,
                paymentOrderId,
                attemptNo,
                channelReference);
    }

    public int cancelPendingAdjustments(OffsetDateTime resolvedAt, Long orderId) {
        return jdbc.update(
                """
                UPDATE runrun_v2.payment_adjustments
                SET status = 'CANCELLED',
                    resolved_at = ?,
                    version = version + 1
                WHERE order_id = ?
                    AND status IN ('PENDING_SUPPLEMENT', 'PENDING_REFUND')
                """,
                resolvedAt,
                orderId);
    }

    public int markRefundProcessing(Long refundId) {
        return jdbc.update(
                """
                UPDATE runrun_v2.refund_orders
                SET status = 'PROCESSING',
                    version = version + 1
                WHERE id = ?
                """,
                refundId);
    }

    public Long countAdjustmentById(Long adjustmentId) {
        return jdbc.queryForObject(
                """
                SELECT count(*)
                FROM runrun_v2.payment_adjustments
                WHERE id = ?
                """,
                Long.class,
                adjustmentId);
    }

    public Long countPendingAdjustments(Long orderId) {
        return jdbc.queryForObject(
                """
                SELECT count(*)
                FROM runrun_v2.payment_adjustments
                WHERE order_id = ?
                    AND status IN ('PENDING_SUPPLEMENT', 'PENDING_REFUND')
                """,
                Long.class,
                orderId);
    }

    public int insertApprovedAdjustment(
            Long adjustmentId,
            Long orderId,
            long deltaCents,
            String status,
            Long approvedBy,
            String reason,
            OffsetDateTime approvedAt) {
        return jdbc.update(
                """
                INSERT INTO runrun_v2.payment_adjustments (
                    id,
                    order_id,
                    delta_cents,
                    status,
                    approved_by,
                    reason,
                    approved_at
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?
                )
                """,
                adjustmentId,
                orderId,
                deltaCents,
                status,
                approvedBy,
                reason,
                approvedAt);
    }

    public int updateEffectiveFeeAndClearConfirmation(
            long effectiveFeeCents, OffsetDateTime updatedAt, Long orderId) {
        return jdbc.update(
                """
                UPDATE runrun_v2.v2_orders
                SET effective_fee_cents = ?,
                    confirm_started_at = NULL,
                    confirm_deadline = NULL,
                    version = version + 1,
                    updated_at = ?
                WHERE id = ?
                """,
                effectiveFeeCents,
                updatedAt,
                orderId);
    }

    public String insertSupplementPayment(
            Long orderId, Long adjustmentId, long amountCents, OffsetDateTime deadline) {
        return jdbc.queryForObject(
                """
                INSERT INTO runrun_v2.payment_orders (
                    order_id,
                    adjustment_id,
                    purpose,
                    amount_cents,
                    deadline
                )
                VALUES (
                    ?,
                    ?,
                    'SUPPLEMENT',
                    ?,
                    ?
                )
                RETURNING id::text
                """,
                String.class,
                orderId,
                adjustmentId,
                amountCents,
                deadline);
    }

    public List<String> findExpiredUnpaidOrderIds(OffsetDateTime deadlineCutoff) {
        return jdbc.queryForList(
                """
                SELECT id::text
                FROM runrun_v2.v2_orders
                WHERE status = 'PENDING_PAYMENT'
                    AND pending_payment_deadline <= ?
                ORDER BY id
                LIMIT 100
                """,
                String.class,
                deadlineCutoff);
    }

    public int closeUnpaidOriginalPayments(OffsetDateTime updatedAt, Long orderId) {
        return jdbc.update(
                """
                UPDATE runrun_v2.payment_orders
                SET status = 'CLOSED',
                    version = version + 1,
                    updated_at = ?
                WHERE order_id = ?
                    AND purpose = 'ORIGINAL'
                    AND status NOT IN ('PAID', 'CLOSED')
                """,
                updatedAt,
                orderId);
    }

    public int updateAttemptResult(String status, OffsetDateTime resultReceivedAt, Long attemptId) {
        return jdbc.update(
                """
                UPDATE runrun_v2.payment_attempts
                SET status = ?,
                    result_received_at = ?,
                    version = version + 1
                WHERE id = ?
                """,
                status,
                resultReceivedAt,
                attemptId);
    }

    public int markPaymentPaid(
            OffsetDateTime paidAt,
            Long creditedAttemptId,
            OffsetDateTime updatedAt,
            Long paymentOrderId) {
        return jdbc.update(
                """
                UPDATE runrun_v2.payment_orders
                SET status = 'PAID',
                    paid_at = ?,
                    credited_attempt_id = ?,
                    version = version + 1,
                    updated_at = ?
                WHERE id = ?
                """,
                paidAt,
                creditedAttemptId,
                updatedAt,
                paymentOrderId);
    }

    public int markOrderPendingAssignment(OffsetDateTime updatedAt, Long orderId) {
        return jdbc.update(
                """
                UPDATE runrun_v2.v2_orders
                SET status = 'PENDING_ASSIGNMENT',
                    version = version + 1,
                    updated_at = ?
                WHERE id = ?
                """,
                updatedAt,
                orderId);
    }

    public int updateRefundResult(String status, OffsetDateTime succeededAt, Long refundId) {
        return jdbc.update(
                """
                UPDATE runrun_v2.refund_orders
                SET status = ?,
                    succeeded_at = ?,
                    version = version + 1
                WHERE id = ?
                """,
                status,
                succeededAt,
                refundId);
    }

    public Long countUnfinishedAdjustmentRefunds(Long adjustmentId) {
        return jdbc.queryForObject(
                """
                SELECT count(*)
                FROM runrun_v2.refund_orders
                WHERE adjustment_id = ?
                    AND status <> 'SUCCEEDED'
                """,
                Long.class,
                adjustmentId);
    }

    public List<String> findRefundIdsByBusinessKey(String businessKey) {
        return jdbc.queryForList(
                """
                SELECT id::text
                FROM runrun_v2.refund_orders
                WHERE business_key = ?
                """,
                String.class,
                businessKey);
    }

    public String insertRefund(
            Long orderId,
            Long paymentOrderId,
            Long sourceAttemptId,
            Long adjustmentId,
            long amountCents,
            String purpose,
            String reason,
            String businessKey,
            String channelReference) {
        return jdbc.queryForObject(
                """
                INSERT INTO runrun_v2.refund_orders (
                    order_id,
                    payment_order_id,
                    source_attempt_id,
                    adjustment_id,
                    amount_cents,
                    purpose,
                    reason,
                    business_key,
                    channel_reference
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?
                )
                RETURNING id::text
                """,
                String.class,
                orderId,
                paymentOrderId,
                sourceAttemptId,
                adjustmentId,
                amountCents,
                purpose,
                reason,
                businessKey,
                channelReference);
    }

    public int completePendingAdjustment(OffsetDateTime resolvedAt, Long adjustmentId) {
        return jdbc.update(
                """
                UPDATE runrun_v2.payment_adjustments
                SET status = 'COMPLETED',
                    resolved_at = ?,
                    version = version + 1
                WHERE id = ?
                    AND status IN ('PENDING_SUPPLEMENT', 'PENDING_REFUND')
                """,
                resolvedAt,
                adjustmentId);
    }

    public String findAdjustmentOrderId(Long adjustmentId) {
        return jdbc.queryForObject(
                """
                SELECT order_id::text
                FROM runrun_v2.payment_adjustments
                WHERE id = ?
                """,
                String.class,
                adjustmentId);
    }

    public Boolean isSupplementPending(Long adjustmentId) {
        return jdbc.queryForObject(
                """
                SELECT status = 'PENDING_SUPPLEMENT'
                FROM runrun_v2.payment_adjustments
                WHERE id = ?
                """,
                Boolean.class,
                adjustmentId);
    }

    public Long sumReservedRefundCents(Long sourceAttemptId) {
        return jdbc.queryForObject(
                """
                SELECT coalesce(sum(amount_cents), 0)
                FROM runrun_v2.refund_orders
                WHERE source_attempt_id = ?
                """,
                Long.class,
                sourceAttemptId);
    }

    public String findCreditedAttemptId(Long paymentOrderId) {
        return jdbc.queryForObject(
                """
                SELECT credited_attempt_id::text
                FROM runrun_v2.payment_orders
                WHERE id = ?
                """,
                String.class,
                paymentOrderId);
    }

    public int cancelOrderAndClearConfirmation(
            OffsetDateTime cancelledAt, OffsetDateTime updatedAt, Long orderId) {
        return jdbc.update(
                """
                UPDATE runrun_v2.v2_orders
                SET status = 'CANCELLED',
                    cancelled_at = ?,
                    confirm_started_at = NULL,
                    confirm_deadline = NULL,
                    version = version + 1,
                    updated_at = ?
                WHERE id = ?
                """,
                cancelledAt,
                updatedAt,
                orderId);
    }

    public List<Map<String, Object>> lockOrderNotificationRecipient(long orderId) {
        return jdbc.queryForList(
                """
                SELECT id
                FROM runrun_v2.app_users
                WHERE id = (
                    SELECT user_id
                    FROM runrun_v2.v2_orders
                    WHERE id = ?)
                FOR KEY SHARE
                """,
                orderId);
    }

    public int updatePaymentStatusIfDifferent(
            String status, OffsetDateTime updatedAt, Long paymentOrderId, String excludedStatus) {
        return jdbc.update(
                """
                UPDATE runrun_v2.payment_orders
                SET status = ?,
                    version = version + 1,
                    updated_at = ?
                WHERE id = ?
                    AND status <> ?
                """,
                status,
                updatedAt,
                paymentOrderId,
                excludedStatus);
    }

    /** 读取并锁定 runrun_v2.payment_orders 的匹配行；锁持有至调用方事务结束。 */
    public List<PaymentOrderVO> findPayment(boolean lock, long paymentOrderId) {
        return jdbc.query(
                """
                SELECT *
                FROM runrun_v2.payment_orders
                WHERE id = ?
                """
                        + (lock
                                ? """
                                    FOR UPDATE
                                    """
                                : ""),
                (r, n) ->
                        new PaymentOrderVO(
                                r.getString("id"),
                                r.getString("order_id"),
                                r.getString("adjustment_id"),
                                r.getString("purpose"),
                                r.getLong("amount_cents"),
                                r.getString("status"),
                                r.getObject("deadline", OffsetDateTime.class),
                                r.getObject("paid_at", OffsetDateTime.class),
                                r.getLong("version")),
                paymentOrderId);
    }

    public List<PaymentAttemptVO> findAttempt(long attemptId) {
        return jdbc.query(
                """
                SELECT *
                FROM runrun_v2.payment_attempts
                WHERE id = ?
                """,
                (r, n) ->
                        new PaymentAttemptVO(
                                r.getString("id"),
                                r.getString("payment_order_id"),
                                r.getInt("attempt_no"),
                                r.getString("status"),
                                r.getLong("version")),
                attemptId);
    }

    public List<RefundOrderVO> findRefund(long refundId) {
        return jdbc.query(
                """
                SELECT *
                FROM runrun_v2.refund_orders
                WHERE id = ?
                """,
                (r, n) ->
                        new RefundOrderVO(
                                r.getString("id"),
                                r.getString("order_id"),
                                r.getString("payment_order_id"),
                                r.getString("adjustment_id"),
                                r.getLong("amount_cents"),
                                r.getString("purpose"),
                                r.getString("status"),
                                r.getObject("succeeded_at", OffsetDateTime.class),
                                r.getLong("version")),
                refundId);
    }

    public List<String> findAttemptIds(Long paymentOrderId) {
        return jdbc.queryForList(
                """
                SELECT id::text
                FROM runrun_v2.payment_attempts
                WHERE payment_order_id = ?
                ORDER BY attempt_no
                """,
                String.class,
                paymentOrderId);
    }

    public List<String> findPaymentIdsByOrder(Long orderId) {
        return jdbc.queryForList(
                """
                SELECT id::text
                FROM runrun_v2.payment_orders
                WHERE order_id = ?
                ORDER BY id
                """,
                String.class,
                orderId);
    }

    public List<String> findRefundIdsByOrder(Long orderId) {
        return jdbc.queryForList(
                """
                SELECT id::text
                FROM runrun_v2.refund_orders
                WHERE order_id = ?
                ORDER BY id
                """,
                String.class,
                orderId);
    }

    public int insertResultEvent(
            String eventId, String resourceType, Long resourceId, String result) {
        return jdbc.update(
                """
                INSERT INTO runrun_v2.payment_result_events (
                    event_id,
                    resource_type,
                    resource_id,
                    result
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    ?
                )
                """,
                eventId,
                resourceType,
                resourceId,
                result);
    }

    public List<Object> lockResultEvent(String eventId) {
        return jdbc.query(
                """
                SELECT pg_advisory_xact_lock(hashtextextended(?, 0))
                """,
                (RowMapper<Object>) (r, n) -> null,
                eventId);
    }

    public List<Map<String, Object>> findResultEvent(String eventId) {
        return jdbc.queryForList(
                """
                SELECT resource_type,
                    resource_id,
                    result
                FROM runrun_v2.payment_result_events
                WHERE event_id = ?
                """,
                eventId);
    }

    public String findRefundSourceAttemptId(Long refundId) {
        return jdbc.queryForObject(
                """
                SELECT source_attempt_id::text
                FROM runrun_v2.refund_orders
                WHERE id = ?
                """,
                String.class,
                refundId);
    }

    public Long sumSucceededRefundCents(Long sourceAttemptId) {
        return jdbc.queryForObject(
                """
                SELECT sum(amount_cents)
                FROM runrun_v2.refund_orders
                WHERE source_attempt_id = ?
                    AND status = 'SUCCEEDED'
                """,
                Long.class,
                sourceAttemptId);
    }

    public Long sumRecordedPlatformRefundCents(String sourceAttemptId) {
        return jdbc.queryForObject(
                """
                SELECT coalesce(sum(platform_cents), 0)
                FROM runrun_v2.payment_financial_events
                WHERE payload ->> 'sourceAttemptId' = ?
                """,
                Long.class,
                sourceAttemptId);
    }

    public int insertRefundFinancialEvent(
            Long orderId,
            String eventKey,
            String kind,
            long amountCents,
            long platformCents,
            long courierCents,
            String payload) {
        return jdbc.update(
                """
                INSERT INTO runrun_v2.payment_financial_events (
                    order_id,
                    event_key,
                    kind,
                    amount_cents,
                    platform_cents,
                    courier_cents,
                    payload
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?::jsonb
                )
                """,
                orderId,
                eventKey,
                kind,
                amountCents,
                platformCents,
                courierCents,
                payload);
    }

    public int insertOrderEventIfAbsent(
            Long orderId, Long actorId, String eventType, String requestId, String payload) {
        return jdbc.update(
                """
                INSERT INTO runrun_v2.v2_order_events (
                    order_id,
                    actor_id,
                    event_type,
                    request_id,
                    payload
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    ?,
                    ?::jsonb
                )
                ON CONFLICT DO NOTHING
                """,
                orderId,
                actorId,
                eventType,
                requestId,
                payload);
    }

    public int insertFinancialEventIfAbsent(
            Long orderId,
            String eventKey,
            String kind,
            long amountCents,
            long platformCents,
            long courierCents,
            String payload) {
        return jdbc.update(
                """
                INSERT INTO runrun_v2.payment_financial_events (
                    order_id,
                    event_key,
                    kind,
                    amount_cents,
                    platform_cents,
                    courier_cents,
                    payload
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?::jsonb
                )
                ON CONFLICT DO NOTHING
                """,
                orderId,
                eventKey,
                kind,
                amountCents,
                platformCents,
                courierCents,
                payload);
    }

    public int insertOrderNotificationIfAbsent(String eventKey, String payload, Long orderId) {
        return jdbc.update(
                """
                INSERT INTO runrun_v2.notification_outbox (
                    recipient_id,
                    event_key,
                    channel,
                    payload
                )
                SELECT user_id,
                    ?,
                    'IN_APP',
                    ?::jsonb
                FROM runrun_v2.v2_orders
                WHERE id = ?
                ON CONFLICT DO NOTHING
                """,
                eventKey,
                payload,
                orderId);
    }

    public int insertAdminAudit(
            Long actorId, String action, Long resourceId, String requestId, String reason) {
        return jdbc.update(
                """
                INSERT INTO runrun_v2.payment_admin_audit (
                    actor_id,
                    action,
                    resource_id,
                    request_id,
                    reason
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    ?,
                    ?
                )
                """,
                actorId,
                action,
                resourceId,
                requestId,
                reason);
    }
}
