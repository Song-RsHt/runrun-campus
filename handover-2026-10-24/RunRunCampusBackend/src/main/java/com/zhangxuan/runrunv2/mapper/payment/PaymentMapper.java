package com.zhangxuan.runrunv2.mapper.payment;

import static com.zhangxuan.runrunv2.model.payment.PaymentModels.*;

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

  public java.lang.String createValuePaymentOrders(
      java.lang.Long parameter2, long amountCents, java.time.OffsetDateTime deadline) {
    return jdbc.queryForObject(
        "INSERT INTO runrun_v2.payment_orders(order_id,purpose,amount_cents,deadline)"
            + " VALUES(?,'ORIGINAL',?,?) RETURNING id::text",
        String.class,
        parameter2,
        amountCents,
        deadline);
  }

  public java.lang.Integer startValuePaymentAttempts(java.lang.Long parameter2) {
    return jdbc.queryForObject(
        "SELECT coalesce(max(attempt_no),0)+1 FROM runrun_v2.payment_attempts WHERE"
            + " payment_order_id=?",
        Integer.class,
        parameter2);
  }

  public java.lang.String startValuePaymentAttempts2(
      java.lang.Long parameter2, int number, java.lang.String parameter4) {
    return jdbc.queryForObject(
        "INSERT INTO"
            + " runrun_v2.payment_attempts(payment_order_id,attempt_no,status,channel_reference)"
            + " VALUES(?,?,'PROCESSING',?) RETURNING id::text",
        String.class,
        parameter2,
        number,
        parameter4);
  }

  public int cancelWritePaymentAdjustments(
      java.time.OffsetDateTime parameter1, java.lang.Long parameter2) {
    return jdbc.update(
        "UPDATE runrun_v2.payment_adjustments SET"
            + " status='CANCELLED',resolved_at=?,version=version+1 WHERE order_id=? AND status IN"
            + " ('PENDING_SUPPLEMENT','PENDING_REFUND')",
        parameter1,
        parameter2);
  }

  public int retryRefundWriteRefundOrders(java.lang.Long parameter1) {
    return jdbc.update(
        "UPDATE runrun_v2.refund_orders SET status='PROCESSING',version=version+1 WHERE id=?",
        parameter1);
  }

  public java.lang.Long approvedAdjustmentValuePaymentAdjustments(java.lang.Long parameter2) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM runrun_v2.payment_adjustments WHERE id=?", Long.class, parameter2);
  }

  public java.lang.Long approvedAdjustmentValuePaymentAdjustments2(java.lang.Long parameter2) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM runrun_v2.payment_adjustments WHERE order_id=? AND status IN"
            + " ('PENDING_SUPPLEMENT','PENDING_REFUND')",
        Long.class,
        parameter2);
  }

  public int approvedAdjustmentWritePaymentAdjustments(
      java.lang.Long parameter1,
      java.lang.Long parameter2,
      long deltaCents,
      java.lang.Object parameter4,
      java.lang.Long parameter5,
      java.lang.String reason,
      java.time.OffsetDateTime parameter7) {
    return jdbc.update(
        "INSERT INTO"
            + " runrun_v2.payment_adjustments(id,order_id,delta_cents,status,approved_by,reason,approved_at)"
            + " VALUES(?,?,?,?,?,?,?)",
        parameter1,
        parameter2,
        deltaCents,
        parameter4,
        parameter5,
        reason,
        parameter7);
  }

  public int approvedAdjustmentWriteV(
      long effective, java.time.OffsetDateTime parameter2, java.lang.Long parameter3) {
    return jdbc.update(
        "UPDATE runrun_v2.v2_orders SET"
            + " effective_fee_cents=?,confirm_started_at=NULL,confirm_deadline=NULL,version=version+1,updated_at=?"
            + " WHERE id=?",
        effective,
        parameter2,
        parameter3);
  }

  public java.lang.String approvedAdjustmentValuePaymentOrders(
      java.lang.Long parameter2,
      java.lang.Long parameter3,
      long deltaCents,
      java.time.OffsetDateTime parameter5) {
    return jdbc.queryForObject(
        "INSERT INTO runrun_v2.payment_orders(order_id,adjustment_id,purpose,amount_cents,deadline)"
            + " VALUES(?,?,'SUPPLEMENT',?,?) RETURNING id::text",
        String.class,
        parameter2,
        parameter3,
        deltaCents,
        parameter5);
  }

  public java.util.List<java.lang.String> closeExpiredRowsV(java.time.OffsetDateTime parameter2) {
    return jdbc.queryForList(
        "SELECT id::text FROM runrun_v2.v2_orders WHERE status='PENDING_PAYMENT' AND"
            + " pending_payment_deadline<=? ORDER BY id LIMIT 100",
        String.class,
        parameter2);
  }

  public int closeExpiredWritePaymentOrders(
      java.time.OffsetDateTime parameter1, java.lang.Long parameter2) {
    return jdbc.update(
        "UPDATE runrun_v2.payment_orders SET status='CLOSED',version=version+1,updated_at=? WHERE"
            + " order_id=? AND purpose='ORIGINAL' AND status NOT IN ('PAID','CLOSED')",
        parameter1,
        parameter2);
  }

  public int applyPaymentWritePaymentAttempts(
      java.lang.String status, java.time.OffsetDateTime parameter2, java.lang.Long parameter3) {
    return jdbc.update(
        "UPDATE runrun_v2.payment_attempts SET status=?,result_received_at=?,version=version+1"
            + " WHERE id=?",
        status,
        parameter2,
        parameter3);
  }

  public int applyPaymentWritePaymentOrders(
      java.time.OffsetDateTime parameter1,
      java.lang.Long parameter2,
      java.time.OffsetDateTime parameter3,
      java.lang.Long parameter4) {
    return jdbc.update(
        "UPDATE runrun_v2.payment_orders SET"
            + " status='PAID',paid_at=?,credited_attempt_id=?,version=version+1,updated_at=? WHERE"
            + " id=?",
        parameter1,
        parameter2,
        parameter3,
        parameter4);
  }

  public int applyPaymentWriteV(java.time.OffsetDateTime parameter1, java.lang.Long parameter2) {
    return jdbc.update(
        "UPDATE runrun_v2.v2_orders SET status='PENDING_ASSIGNMENT',version=version+1,updated_at=?"
            + " WHERE id=?",
        parameter1,
        parameter2);
  }

  public int applyRefundWriteRefundOrders(
      java.lang.String status, java.lang.Object parameter2, java.lang.Long parameter3) {
    return jdbc.update(
        "UPDATE runrun_v2.refund_orders SET status=?,succeeded_at=?,version=version+1 WHERE id=?",
        status,
        parameter2,
        parameter3);
  }

  public java.lang.Long applyRefundValueRefundOrders(java.lang.Long parameter2) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM runrun_v2.refund_orders WHERE adjustment_id=? AND"
            + " status<>'SUCCEEDED'",
        Long.class,
        parameter2);
  }

  public java.util.List<java.lang.String> createRefundRowsRefundOrders(
      java.lang.String businessKey) {
    return jdbc.queryForList(
        "SELECT id::text FROM runrun_v2.refund_orders WHERE business_key=?",
        String.class,
        businessKey);
  }

  public java.lang.String createRefundValueRefundOrders(
      java.lang.Long parameter2,
      java.lang.Long parameter3,
      java.lang.Long parameter4,
      java.lang.Long parameter5,
      long cents,
      java.lang.String purpose,
      java.lang.String reason,
      java.lang.String businessKey,
      java.lang.String parameter10) {
    return jdbc.queryForObject(
        "INSERT INTO"
            + " runrun_v2.refund_orders(order_id,payment_order_id,source_attempt_id,adjustment_id,amount_cents,purpose,reason,business_key,channel_reference)"
            + " VALUES(?,?,?,?,?,?,?,?,?) RETURNING id::text",
        String.class,
        parameter2,
        parameter3,
        parameter4,
        parameter5,
        cents,
        purpose,
        reason,
        businessKey,
        parameter10);
  }

  public int resolveAdjustmentWritePaymentAdjustments(
      java.time.OffsetDateTime parameter1, java.lang.Long parameter2) {
    return jdbc.update(
        "UPDATE runrun_v2.payment_adjustments SET"
            + " status='COMPLETED',resolved_at=?,version=version+1 WHERE id=? AND status IN"
            + " ('PENDING_SUPPLEMENT','PENDING_REFUND')",
        parameter1,
        parameter2);
  }

  public java.lang.String resolveAdjustmentValuePaymentAdjustments(java.lang.Long parameter2) {
    return jdbc.queryForObject(
        "SELECT order_id::text FROM runrun_v2.payment_adjustments WHERE id=?",
        String.class,
        parameter2);
  }

  public java.lang.Boolean adjustmentOpenValuePaymentAdjustments(java.lang.Long parameter2) {
    return jdbc.queryForObject(
        "SELECT status='PENDING_SUPPLEMENT' FROM runrun_v2.payment_adjustments WHERE id=?",
        Boolean.class,
        parameter2);
  }

  public java.lang.Long availableValueRefundOrders(java.lang.Long parameter2) {
    return jdbc.queryForObject(
        "SELECT coalesce(sum(amount_cents),0) FROM runrun_v2.refund_orders WHERE"
            + " source_attempt_id=?",
        Long.class,
        parameter2);
  }

  public java.lang.String creditedValuePaymentOrders(java.lang.Long parameter2) {
    return jdbc.queryForObject(
        "SELECT credited_attempt_id::text FROM runrun_v2.payment_orders WHERE id=?",
        String.class,
        parameter2);
  }

  public int closeOrderWriteV(
      java.time.OffsetDateTime parameter1,
      java.time.OffsetDateTime parameter2,
      java.lang.Long parameter3) {
    return jdbc.update(
        "UPDATE runrun_v2.v2_orders SET"
            + " status='CANCELLED',cancelled_at=?,confirm_started_at=NULL,confirm_deadline=NULL,version=version+1,updated_at=?"
            + " WHERE id=?",
        parameter1,
        parameter2,
        parameter3);
  }

  public java.util.List<java.util.Map<java.lang.String, java.lang.Object>>
      lockNotificationRecipientRowsAppUsers(long parameter1) {
    return jdbc.queryForList(
        "SELECT id FROM runrun_v2.app_users WHERE id=(SELECT user_id FROM runrun_v2.v2_orders WHERE"
            + " id=?) FOR KEY SHARE",
        parameter1);
  }

  public int paymentStatusWritePaymentOrders(
      java.lang.String status,
      java.time.OffsetDateTime parameter2,
      java.lang.Long parameter3,
      java.lang.String binding4) {
    return jdbc.update(
        "UPDATE runrun_v2.payment_orders SET status=?,version=version+1,updated_at=? WHERE id=? AND"
            + " status<>?",
        status,
        parameter2,
        parameter3,
        binding4);
  }

  public java.util.List<com.zhangxuan.runrunv2.model.payment.PaymentModels.Payment>
      paymentRowsPaymentOrders(boolean lock, long parameter2) {
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
        parameter2);
  }

  public java.util.List<com.zhangxuan.runrunv2.model.payment.PaymentModels.Attempt>
      attemptRowsPaymentAttempts(long parameter2) {
    return jdbc.query(
        "SELECT * FROM runrun_v2.payment_attempts WHERE id=?",
        (r, n) ->
            new Attempt(
                r.getString("id"),
                r.getString("payment_order_id"),
                r.getInt("attempt_no"),
                r.getString("status"),
                r.getLong("version")),
        parameter2);
  }

  public java.util.List<com.zhangxuan.runrunv2.model.payment.PaymentModels.Refund>
      refundRowsRefundOrders(long parameter2) {
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
        parameter2);
  }

  public java.util.List<java.lang.String> attemptsRowsPaymentAttempts(java.lang.Long parameter2) {
    return jdbc.queryForList(
        "SELECT id::text FROM runrun_v2.payment_attempts WHERE payment_order_id=? ORDER BY"
            + " attempt_no",
        String.class,
        parameter2);
  }

  public java.util.List<java.lang.String> paymentsForOrderRowsPaymentOrders(
      java.lang.Long parameter2) {
    return jdbc.queryForList(
        "SELECT id::text FROM runrun_v2.payment_orders WHERE order_id=? ORDER BY id",
        String.class,
        parameter2);
  }

  public java.util.List<java.lang.String> refundsForOrderRowsRefundOrders(
      java.lang.Long parameter2) {
    return jdbc.queryForList(
        "SELECT id::text FROM runrun_v2.refund_orders WHERE order_id=? ORDER BY id",
        String.class,
        parameter2);
  }

  public int seenWritePaymentResultEvents(
      java.lang.String eventId,
      java.lang.String type,
      java.lang.Long parameter3,
      java.lang.String parameter4) {
    return jdbc.update(
        "INSERT INTO runrun_v2.payment_result_events(event_id,resource_type,resource_id,result)"
            + " VALUES(?,?,?,?)",
        eventId,
        type,
        parameter3,
        parameter4);
  }

  public java.util.List<java.lang.Object> knownEventRowsStatement(java.lang.String parameter2) {
    return jdbc.query(
        "SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
        (org.springframework.jdbc.core.RowMapper<Object>) (r, n) -> null,
        parameter2);
  }

  public java.util.List<java.util.Map<java.lang.String, java.lang.Object>>
      knownEventRowsPaymentResultEvents(java.lang.String eventId) {
    return jdbc.queryForList(
        "SELECT resource_type,resource_id,result FROM runrun_v2.payment_result_events WHERE"
            + " event_id=?",
        eventId);
  }

  public java.lang.String refundFinancialValueRefundOrders(java.lang.Long parameter2) {
    return jdbc.queryForObject(
        "SELECT source_attempt_id::text FROM runrun_v2.refund_orders WHERE id=?",
        String.class,
        parameter2);
  }

  public java.lang.Long refundFinancialValueRefundOrders2(java.lang.Long parameter2) {
    return jdbc.queryForObject(
        "SELECT sum(amount_cents) FROM runrun_v2.refund_orders WHERE source_attempt_id=? AND"
            + " status='SUCCEEDED'",
        Long.class,
        parameter2);
  }

  public java.lang.Long refundFinancialValuePaymentFinancialEvents(java.lang.String source) {
    return jdbc.queryForObject(
        "SELECT coalesce(sum(platform_cents),0) FROM runrun_v2.payment_financial_events WHERE"
            + " payload->>'sourceAttemptId'=?",
        Long.class,
        source);
  }

  public int refundFinancialWritePaymentFinancialEvents(
      java.lang.Long parameter1,
      java.lang.String parameter2,
      java.lang.String parameter3,
      long parameter4,
      long platform,
      long parameter6,
      java.lang.String parameter7) {
    return jdbc.update(
        "INSERT INTO"
            + " runrun_v2.payment_financial_events(order_id,event_key,kind,amount_cents,platform_cents,courier_cents,payload)"
            + " VALUES(?,?,?,?,?,?,?::jsonb)",
        parameter1,
        parameter2,
        parameter3,
        parameter4,
        platform,
        parameter6,
        parameter7);
  }

  public int eventWriteV(
      java.lang.Long parameter1,
      java.lang.Long parameter2,
      java.lang.String type,
      java.lang.String key,
      java.lang.String parameter5) {
    return jdbc.update(
        "INSERT INTO runrun_v2.v2_order_events(order_id,actor_id,event_type,request_id,payload)"
            + " VALUES(?,?,?,?,?::jsonb) ON CONFLICT DO NOTHING",
        parameter1,
        parameter2,
        type,
        key,
        parameter5);
  }

  public int financialWritePaymentFinancialEvents(
      java.lang.Long parameter1,
      java.lang.String key,
      java.lang.String kind,
      long cents,
      long platform,
      long parameter6,
      java.lang.String parameter7) {
    return jdbc.update(
        "INSERT INTO"
            + " runrun_v2.payment_financial_events(order_id,event_key,kind,amount_cents,platform_cents,courier_cents,payload)"
            + " VALUES(?,?,?,?,?,?,?::jsonb) ON CONFLICT DO NOTHING",
        parameter1,
        key,
        kind,
        cents,
        platform,
        parameter6,
        parameter7);
  }

  public int notifyUserWriteNotificationOutbox(
      java.lang.String key, java.lang.String parameter2, java.lang.Long parameter3) {
    return jdbc.update(
        "INSERT INTO runrun_v2.notification_outbox(recipient_id,event_key,channel,payload) SELECT"
            + " user_id,?,'IN_APP',?::jsonb FROM runrun_v2.v2_orders WHERE id=? ON CONFLICT DO"
            + " NOTHING",
        key,
        parameter2,
        parameter3);
  }

  public int auditWritePaymentAdminAudit(
      java.lang.Long parameter1,
      java.lang.String action,
      java.lang.Long parameter3,
      java.lang.String key,
      java.lang.String reason) {
    return jdbc.update(
        "INSERT INTO runrun_v2.payment_admin_audit(actor_id,action,resource_id,request_id,reason)"
            + " VALUES(?,?,?,?,?)",
        parameter1,
        action,
        parameter3,
        key,
        reason);
  }
}
