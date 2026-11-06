package com.zhangxuan.runrunv2.mapper.payment;

import static com.zhangxuan.runrunv2.dto.response.payment.PaymentModels.*;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

/**
 * SQL for PaymentService. Uses the caller's transaction-bound JDBC connection; never opens or
 * commits a transaction.
 */
@Repository
public class PaymentMapper {

  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  public PaymentMapper(org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public String createValuePaymentOrders(Long orderId, long amountCents, OffsetDateTime deadline) {
    return jdbc.queryForObject(
        "INSERT INTO runrun_v2.payment_orders(order_id,purpose,amount_cents,deadline)"
            + " VALUES(?,'ORIGINAL',?,?) RETURNING id::text",
        String.class,
        orderId,
        amountCents,
        deadline);
  }

  public Integer startValuePaymentAttempts(Long pId) {
    return jdbc.queryForObject(
        "SELECT coalesce(max(attempt_no),0)+1 FROM runrun_v2.payment_attempts WHERE"
            + " payment_order_id=?",
        Integer.class,
        pId);
  }

  public String startValuePaymentAttempts2(
      Long paymentOrderId, int attemptNo, String channelReference) {
    return jdbc.queryForObject(
        "INSERT INTO"
            + " runrun_v2.payment_attempts(payment_order_id,attempt_no,status,channel_reference)"
            + " VALUES(?,?,'PROCESSING',?) RETURNING id::text",
        String.class,
        paymentOrderId,
        attemptNo,
        channelReference);
  }

  public int cancelWritePaymentAdjustments(OffsetDateTime timestamp, Long id) {
    return jdbc.update(
        "UPDATE runrun_v2.payment_adjustments SET"
            + " status='CANCELLED',resolved_at=?,version=version+1 WHERE order_id=? AND status IN"
            + " ('PENDING_SUPPLEMENT','PENDING_REFUND')",
        timestamp,
        id);
  }

  public int retryRefundWriteRefundOrders(Long id) {
    return jdbc.update(
        "UPDATE runrun_v2.refund_orders SET status='PROCESSING',version=version+1 WHERE id=?", id);
  }

  public Long approvedAdjustmentValuePaymentAdjustments(Long adjustmentId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM runrun_v2.payment_adjustments WHERE id=?", Long.class, adjustmentId);
  }

  public Long approvedAdjustmentValuePaymentAdjustments2(Long orderId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM runrun_v2.payment_adjustments WHERE order_id=? AND status IN"
            + " ('PENDING_SUPPLEMENT','PENDING_REFUND')",
        Long.class,
        orderId);
  }

  public int approvedAdjustmentWritePaymentAdjustments(
      Long id,
      Long orderId,
      long deltaCents,
      Object status,
      Long approvedBy,
      String reason,
      OffsetDateTime approvedAt) {
    return jdbc.update(
        "INSERT INTO"
            + " runrun_v2.payment_adjustments(id,order_id,delta_cents,status,approved_by,reason,approved_at)"
            + " VALUES(?,?,?,?,?,?,?)",
        id,
        orderId,
        deltaCents,
        status,
        approvedBy,
        reason,
        approvedAt);
  }

  public int approvedAdjustmentWriteV(long effective, OffsetDateTime timestamp, Long orderId) {
    return jdbc.update(
        "UPDATE runrun_v2.v2_orders SET"
            + " effective_fee_cents=?,confirm_started_at=NULL,confirm_deadline=NULL,version=version+1,updated_at=?"
            + " WHERE id=?",
        effective,
        timestamp,
        orderId);
  }

  public String approvedAdjustmentValuePaymentOrders(
      Long orderId, Long adjustmentId, long amountCents, OffsetDateTime deadline) {
    return jdbc.queryForObject(
        "INSERT INTO runrun_v2.payment_orders(order_id,adjustment_id,purpose,amount_cents,deadline)"
            + " VALUES(?,?,'SUPPLEMENT',?,?) RETURNING id::text",
        String.class,
        orderId,
        adjustmentId,
        amountCents,
        deadline);
  }

  public List<String> closeExpiredRowsV(OffsetDateTime timestamp) {
    return jdbc.queryForList(
        "SELECT id::text FROM runrun_v2.v2_orders WHERE status='PENDING_PAYMENT' AND"
            + " pending_payment_deadline<=? ORDER BY id LIMIT 100",
        String.class,
        timestamp);
  }

  public int closeExpiredWritePaymentOrders(OffsetDateTime timestamp, Long id) {
    return jdbc.update(
        "UPDATE runrun_v2.payment_orders SET status='CLOSED',version=version+1,updated_at=? WHERE"
            + " order_id=? AND purpose='ORIGINAL' AND status NOT IN ('PAID','CLOSED')",
        timestamp,
        id);
  }

  public int applyPaymentWritePaymentAttempts(String status, OffsetDateTime timestamp, Long aId) {
    return jdbc.update(
        "UPDATE runrun_v2.payment_attempts SET status=?,result_received_at=?,version=version+1"
            + " WHERE id=?",
        status,
        timestamp,
        aId);
  }

  public int applyPaymentWritePaymentOrders(
      OffsetDateTime timestamp, Long aId, OffsetDateTime timestamp2, Long pId) {
    return jdbc.update(
        "UPDATE runrun_v2.payment_orders SET"
            + " status='PAID',paid_at=?,credited_attempt_id=?,version=version+1,updated_at=? WHERE"
            + " id=?",
        timestamp,
        aId,
        timestamp2,
        pId);
  }

  public int applyPaymentWriteV(OffsetDateTime timestamp, Long orderId) {
    return jdbc.update(
        "UPDATE runrun_v2.v2_orders SET status='PENDING_ASSIGNMENT',version=version+1,updated_at=?"
            + " WHERE id=?",
        timestamp,
        orderId);
  }

  public int applyRefundWriteRefundOrders(String status, Object value, Long rId) {
    return jdbc.update(
        "UPDATE runrun_v2.refund_orders SET status=?,succeeded_at=?,version=version+1 WHERE id=?",
        status,
        value,
        rId);
  }

  public Long applyRefundValueRefundOrders(Long adjustmentId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM runrun_v2.refund_orders WHERE adjustment_id=? AND"
            + " status<>'SUCCEEDED'",
        Long.class,
        adjustmentId);
  }

  public List<String> createRefundRowsRefundOrders(String businessKey) {
    return jdbc.queryForList(
        "SELECT id::text FROM runrun_v2.refund_orders WHERE business_key=?",
        String.class,
        businessKey);
  }

  public String createRefundValueRefundOrders(
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
        "INSERT INTO"
            + " runrun_v2.refund_orders(order_id,payment_order_id,source_attempt_id,adjustment_id,amount_cents,purpose,reason,business_key,channel_reference)"
            + " VALUES(?,?,?,?,?,?,?,?,?) RETURNING id::text",
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

  public int resolveAdjustmentWritePaymentAdjustments(OffsetDateTime timestamp, Long id) {
    return jdbc.update(
        "UPDATE runrun_v2.payment_adjustments SET"
            + " status='COMPLETED',resolved_at=?,version=version+1 WHERE id=? AND status IN"
            + " ('PENDING_SUPPLEMENT','PENDING_REFUND')",
        timestamp,
        id);
  }

  public String resolveAdjustmentValuePaymentAdjustments(Long id) {
    return jdbc.queryForObject(
        "SELECT order_id::text FROM runrun_v2.payment_adjustments WHERE id=?", String.class, id);
  }

  public Boolean adjustmentOpenValuePaymentAdjustments(Long id) {
    return jdbc.queryForObject(
        "SELECT status='PENDING_SUPPLEMENT' FROM runrun_v2.payment_adjustments WHERE id=?",
        Boolean.class,
        id);
  }

  public Long availableValueRefundOrders(Long source) {
    return jdbc.queryForObject(
        "SELECT coalesce(sum(amount_cents),0) FROM runrun_v2.refund_orders WHERE"
            + " source_attempt_id=?",
        Long.class,
        source);
  }

  public String creditedValuePaymentOrders(Long payment) {
    return jdbc.queryForObject(
        "SELECT credited_attempt_id::text FROM runrun_v2.payment_orders WHERE id=?",
        String.class,
        payment);
  }

  public int closeOrderWriteV(OffsetDateTime timestamp, OffsetDateTime timestamp2, Long id) {
    return jdbc.update(
        "UPDATE runrun_v2.v2_orders SET"
            + " status='CANCELLED',cancelled_at=?,confirm_started_at=NULL,confirm_deadline=NULL,version=version+1,updated_at=?"
            + " WHERE id=?",
        timestamp,
        timestamp2,
        id);
  }

  public List<Map<String, Object>> lockNotificationRecipientRowsAppUsers(long orderId) {
    return jdbc.queryForList(
        "SELECT id FROM runrun_v2.app_users WHERE id=(SELECT user_id FROM runrun_v2.v2_orders WHERE"
            + " id=?) FOR KEY SHARE",
        orderId);
  }

  public int paymentStatusWritePaymentOrders(
      String status, OffsetDateTime timestamp, Long id, String status2) {
    return jdbc.update(
        "UPDATE runrun_v2.payment_orders SET status=?,version=version+1,updated_at=? WHERE id=? AND"
            + " status<>?",
        status,
        timestamp,
        id,
        status2);
  }

  public List<Payment> paymentRowsPaymentOrders(boolean lock, long id) {
    return jdbc.query(
        "SELECT * FROM runrun_v2.payment_orders WHERE id=?" + (lock ? " FOR UPDATE" : ""),
        (r, n) ->
            new Payment(
                r.getString("id"),
                r.getString("order_id"),
                r.getString("adjustment_id"),
                r.getString("purpose"),
                r.getLong("amount_cents"),
                r.getString("status"),
                r.getObject("deadline", OffsetDateTime.class),
                r.getObject("paid_at", OffsetDateTime.class),
                r.getLong("version")),
        id);
  }

  public List<Attempt> attemptRowsPaymentAttempts(long id) {
    return jdbc.query(
        "SELECT * FROM runrun_v2.payment_attempts WHERE id=?",
        (r, n) ->
            new Attempt(
                r.getString("id"),
                r.getString("payment_order_id"),
                r.getInt("attempt_no"),
                r.getString("status"),
                r.getLong("version")),
        id);
  }

  public List<Refund> refundRowsRefundOrders(long id) {
    return jdbc.query(
        "SELECT * FROM runrun_v2.refund_orders WHERE id=?",
        (r, n) ->
            new Refund(
                r.getString("id"),
                r.getString("order_id"),
                r.getString("payment_order_id"),
                r.getString("adjustment_id"),
                r.getLong("amount_cents"),
                r.getString("purpose"),
                r.getString("status"),
                r.getObject("succeeded_at", OffsetDateTime.class),
                r.getLong("version")),
        id);
  }

  public List<String> attemptsRowsPaymentAttempts(Long id) {
    return jdbc.queryForList(
        "SELECT id::text FROM runrun_v2.payment_attempts WHERE payment_order_id=? ORDER BY"
            + " attempt_no",
        String.class,
        id);
  }

  public List<String> paymentsForOrderRowsPaymentOrders(Long id) {
    return jdbc.queryForList(
        "SELECT id::text FROM runrun_v2.payment_orders WHERE order_id=? ORDER BY id",
        String.class,
        id);
  }

  public List<String> refundsForOrderRowsRefundOrders(Long id) {
    return jdbc.queryForList(
        "SELECT id::text FROM runrun_v2.refund_orders WHERE order_id=? ORDER BY id",
        String.class,
        id);
  }

  public int seenWritePaymentResultEvents(
      String eventId, String resourceType, Long resourceId, String result) {
    return jdbc.update(
        "INSERT INTO runrun_v2.payment_result_events(event_id,resource_type,resource_id,result)"
            + " VALUES(?,?,?,?)",
        eventId,
        resourceType,
        resourceId,
        result);
  }

  public List<Object> lockResultEvent(String value) {
    return jdbc.query(
        "SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
        (org.springframework.jdbc.core.RowMapper<Object>) (r, n) -> null,
        value);
  }

  public List<Map<String, Object>> knownEventRowsPaymentResultEvents(String eventId) {
    return jdbc.queryForList(
        "SELECT resource_type,resource_id,result FROM runrun_v2.payment_result_events WHERE"
            + " event_id=?",
        eventId);
  }

  public String refundFinancialValueRefundOrders(Long rId) {
    return jdbc.queryForObject(
        "SELECT source_attempt_id::text FROM runrun_v2.refund_orders WHERE id=?",
        String.class,
        rId);
  }

  public Long refundFinancialValueRefundOrders2(Long source) {
    return jdbc.queryForObject(
        "SELECT sum(amount_cents) FROM runrun_v2.refund_orders WHERE source_attempt_id=? AND"
            + " status='SUCCEEDED'",
        Long.class,
        source);
  }

  public Long refundFinancialValuePaymentFinancialEvents(String source) {
    return jdbc.queryForObject(
        "SELECT coalesce(sum(platform_cents),0) FROM runrun_v2.payment_financial_events WHERE"
            + " payload->>'sourceAttemptId'=?",
        Long.class,
        source);
  }

  public int refundFinancialWritePaymentFinancialEvents(
      Long orderId,
      String eventKey,
      String kind,
      long amountCents,
      long platformCents,
      long courierCents,
      String payload) {
    return jdbc.update(
        "INSERT INTO"
            + " runrun_v2.payment_financial_events(order_id,event_key,kind,amount_cents,platform_cents,courier_cents,payload)"
            + " VALUES(?,?,?,?,?,?,?::jsonb)",
        orderId,
        eventKey,
        kind,
        amountCents,
        platformCents,
        courierCents,
        payload);
  }

  public int eventWriteV(
      Long orderId, Long actorId, String eventType, String requestId, String payload) {
    return jdbc.update(
        "INSERT INTO runrun_v2.v2_order_events(order_id,actor_id,event_type,request_id,payload)"
            + " VALUES(?,?,?,?,?::jsonb) ON CONFLICT DO NOTHING",
        orderId,
        actorId,
        eventType,
        requestId,
        payload);
  }

  public int financialWritePaymentFinancialEvents(
      Long orderId,
      String eventKey,
      String kind,
      long amountCents,
      long platformCents,
      long courierCents,
      String payload) {
    return jdbc.update(
        "INSERT INTO"
            + " runrun_v2.payment_financial_events(order_id,event_key,kind,amount_cents,platform_cents,courier_cents,payload)"
            + " VALUES(?,?,?,?,?,?,?::jsonb) ON CONFLICT DO NOTHING",
        orderId,
        eventKey,
        kind,
        amountCents,
        platformCents,
        courierCents,
        payload);
  }

  public int notifyUserWriteNotificationOutbox(String key, String write, Long order) {
    return jdbc.update(
        "INSERT INTO runrun_v2.notification_outbox(recipient_id,event_key,channel,payload) SELECT"
            + " user_id,?,'IN_APP',?::jsonb FROM runrun_v2.v2_orders WHERE id=? ON CONFLICT DO"
            + " NOTHING",
        key,
        write,
        order);
  }

  public int auditWritePaymentAdminAudit(
      Long actorId, String action, Long resourceId, String requestId, String reason) {
    return jdbc.update(
        "INSERT INTO runrun_v2.payment_admin_audit(actor_id,action,resource_id,request_id,reason)"
            + " VALUES(?,?,?,?,?)",
        actorId,
        action,
        resourceId,
        requestId,
        reason);
  }
}
