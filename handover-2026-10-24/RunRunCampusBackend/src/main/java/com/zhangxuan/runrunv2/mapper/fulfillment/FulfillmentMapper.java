package com.zhangxuan.runrunv2.mapper.fulfillment;

import static com.zhangxuan.runrunv2.model.fulfillment.FulfillmentModels.*;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

/**
 * SQL for FulfillmentService. Uses the caller's transaction-bound JDBC connection; never opens or
 * commits a transaction.
 */
@Repository
public class FulfillmentMapper {

  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  public FulfillmentMapper(org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public java.lang.String acceptValueFulfillmentTasks(
      java.lang.Long parameter2,
      java.lang.Long parameter3,
      java.time.OffsetDateTime parameter4,
      java.time.OffsetDateTime parameter5) {
    return jdbc.queryForObject(
        "INSERT INTO runrun_v2.fulfillment_tasks(order_id,courier_id,status,accepted_at,created_at)"
            + " VALUES(?,?,'ACTIVE',?,?) RETURNING id::text",
        String.class,
        parameter2,
        parameter3,
        parameter4,
        parameter5);
  }

  public java.util.List<java.util.Map<java.lang.String, java.lang.Object>> inviteRowsAppUsers(
      java.lang.Long parameter1) {
    return jdbc.queryForList(
        "SELECT id FROM runrun_v2.app_users WHERE id=? AND role='COURIER' AND status='ACTIVE' FOR"
            + " KEY SHARE",
        parameter1);
  }

  public java.lang.Long inviteValueFulfillmentTasks(java.lang.Long parameter2) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM runrun_v2.fulfillment_tasks WHERE order_id=? AND status='ACTIVE'",
        Long.class,
        parameter2);
  }

  public java.lang.String inviteValueFulfillmentTasks2(java.lang.Long parameter2) {
    return jdbc.queryForObject(
        "SELECT max(id)::text FROM runrun_v2.fulfillment_tasks WHERE order_id=?",
        String.class,
        parameter2);
  }

  public java.lang.String inviteValueFulfillmentTasks3(
      java.lang.Long parameter2,
      java.lang.Long parameter3,
      java.lang.Long parameter4,
      java.time.OffsetDateTime parameter5) {
    return jdbc.queryForObject(
        "INSERT INTO"
            + " runrun_v2.fulfillment_tasks(order_id,courier_id,status,source_task_id,created_at)"
            + " VALUES(?,?,'OFFERED',?,?) RETURNING id::text",
        String.class,
        parameter2,
        parameter3,
        parameter4,
        parameter5);
  }

  public java.lang.Long respondValueFulfillmentTasks(java.lang.Long parameter2) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM runrun_v2.fulfillment_tasks WHERE order_id=? AND status='ACTIVE'",
        Long.class,
        parameter2);
  }

  public int respondWriteFulfillmentTasks(
      java.time.OffsetDateTime parameter1, java.lang.Long parameter2) {
    return jdbc.update(
        "UPDATE runrun_v2.fulfillment_tasks SET status='ACTIVE',accepted_at=?,version=version+1"
            + " WHERE id=?",
        parameter1,
        parameter2);
  }

  public java.lang.Long releaseValueV(java.lang.Long parameter2) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM runrun_v2.v2_order_proofs WHERE task_id=? AND kind='PICKUP'",
        Long.class,
        parameter2);
  }

  public java.lang.Long proofValueV(java.lang.Long parameter2, java.lang.String parameter3) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM runrun_v2.v2_order_proofs WHERE task_id=? AND kind=?",
        Long.class,
        parameter2,
        parameter3);
  }

  public java.lang.Long proofValueV2(java.lang.String parameter2) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM runrun_v2.v2_order_proofs WHERE storage_key=?",
        Long.class,
        parameter2);
  }

  public java.lang.String proofValueV3(
      java.lang.Long parameter2,
      java.lang.Long parameter3,
      java.lang.Long parameter4,
      java.lang.String parameter5,
      java.lang.String parameter6) {
    return jdbc.queryForObject(
        "INSERT INTO"
            + " runrun_v2.v2_order_proofs(order_id,task_id,uploaded_by,kind,storage_key,storage_status)"
            + " VALUES(?,?,?,?,?,'AVAILABLE') RETURNING id::text",
        String.class,
        parameter2,
        parameter3,
        parameter4,
        parameter5,
        parameter6);
  }

  public java.util.List<java.util.Map<java.lang.String, java.lang.Object>>
      locationRowsLocationEvents(
          java.lang.Long parameter1, java.lang.String parameter2, java.lang.String parameter3) {
    return jdbc.queryForList(
        "SELECT * FROM runrun_v2.location_events WHERE task_id=? AND event_type=? AND request_id=?",
        parameter1,
        parameter2,
        parameter3);
  }

  public java.lang.Long locationValueLocationEvents(java.lang.Long parameter2) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM runrun_v2.location_events WHERE task_id=? AND event_type='DELIVERY'"
            + " AND accepted",
        Long.class,
        parameter2);
  }

  public java.lang.String locationValueLocationEvents2(
      java.lang.Long parameter2,
      java.lang.Long parameter3,
      java.lang.Long parameter4,
      java.lang.String parameter5,
      java.math.BigDecimal parameter6,
      java.math.BigDecimal parameter7,
      java.lang.String parameter8,
      java.time.OffsetDateTime parameter9,
      java.lang.String parameter10,
      boolean accepted,
      java.lang.String rejection) {
    return jdbc.queryForObject(
        "INSERT INTO"
            + " runrun_v2.location_events(order_id,task_id,courier_id,event_type,latitude,longitude,coordinate_system,received_at,request_id,accepted,decision_reason)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,?) RETURNING id::text",
        String.class,
        parameter2,
        parameter3,
        parameter4,
        parameter5,
        parameter6,
        parameter7,
        parameter8,
        parameter9,
        parameter10,
        accepted,
        rejection);
  }

  public int pickupWriteFulfillmentTasks(
      java.time.OffsetDateTime parameter1, java.lang.Long parameter2) {
    return jdbc.update(
        "UPDATE runrun_v2.fulfillment_tasks SET picked_up_at=?,version=version+1 WHERE id=?",
        parameter1,
        parameter2);
  }

  public java.lang.Long deliverValueLocationEvents(
      java.lang.Long parameter2,
      java.lang.Long parameter3,
      java.lang.Long parameter4,
      java.lang.Long parameter5) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM runrun_v2.location_events WHERE id=? AND order_id=? AND task_id=? AND"
            + " courier_id=? AND event_type='DELIVERY' AND accepted AND deleted_at IS NULL",
        Long.class,
        parameter2,
        parameter3,
        parameter4,
        parameter5);
  }

  public int deliverWriteFulfillmentTasks(
      java.time.OffsetDateTime parameter1,
      java.time.OffsetDateTime parameter2,
      java.lang.Long parameter3) {
    return jdbc.update(
        "UPDATE runrun_v2.fulfillment_tasks SET"
            + " status='DELIVERED',delivered_at=?,ended_at=?,version=version+1 WHERE id=?",
        parameter1,
        parameter2,
        parameter3);
  }

  public int deliverWriteV(
      java.time.OffsetDateTime parameter1,
      java.time.OffsetDateTime start,
      java.lang.Object parameter3,
      java.time.OffsetDateTime parameter4,
      java.lang.Long parameter5) {
    return jdbc.update(
        "UPDATE runrun_v2.v2_orders SET"
            + " status='DELIVERED_PENDING_CONFIRMATION',delivered_at=coalesce(delivered_at,?),confirm_started_at=?,confirm_deadline=?,version=version+1,updated_at=?"
            + " WHERE id=?",
        parameter1,
        start,
        parameter3,
        parameter4,
        parameter5);
  }

  public java.util.List<java.lang.String> autoCompleteRowsV(java.time.OffsetDateTime parameter2) {
    return jdbc.queryForList(
        "SELECT id::text FROM runrun_v2.v2_orders WHERE status='DELIVERED_PENDING_CONFIRMATION' AND"
            + " confirm_deadline<=? ORDER BY id LIMIT 100",
        String.class,
        parameter2);
  }

  public int blockCompletionWriteFulfillmentCompletionBlocks(
      java.lang.Long parameter1, java.lang.String type, java.lang.String sourceId) {
    return jdbc.update(
        "INSERT INTO runrun_v2.fulfillment_completion_blocks VALUES(?,?,?) ON CONFLICT DO NOTHING",
        parameter1,
        type,
        sourceId);
  }

  public int unblockCompletionWriteFulfillmentCompletionBlocks(
      java.lang.Long parameter1, java.lang.String type, java.lang.String sourceId) {
    return jdbc.update(
        "DELETE FROM runrun_v2.fulfillment_completion_blocks WHERE order_id=? AND source_type=? AND"
            + " source_id=?",
        parameter1,
        type,
        sourceId);
  }

  public int restartWindowWriteV(
      java.time.OffsetDateTime parameter1,
      java.time.OffsetDateTime parameter2,
      java.time.OffsetDateTime parameter3,
      java.lang.Long parameter4) {
    return jdbc.update(
        "UPDATE runrun_v2.v2_orders SET"
            + " confirm_started_at=?,confirm_deadline=?,version=version+1,updated_at=? WHERE id=?",
        parameter1,
        parameter2,
        parameter3,
        parameter4);
  }

  public int completeWriteV(
      java.time.OffsetDateTime parameter1,
      java.time.OffsetDateTime parameter2,
      java.lang.Long parameter3) {
    return jdbc.update(
        "UPDATE runrun_v2.v2_orders SET"
            + " status='COMPLETED',completed_at=?,version=version+1,updated_at=? WHERE id=?",
        parameter1,
        parameter2,
        parameter3);
  }

  public java.lang.Boolean adjustmentBlockedValueFulfillmentCompletionBlocks(
      java.lang.Long parameter2, java.lang.Long parameter3) {
    return jdbc.queryForObject(
        "SELECT EXISTS(SELECT 1 FROM runrun_v2.fulfillment_completion_blocks WHERE order_id=? AND"
            + " source_type='ADJUSTMENT') OR EXISTS(SELECT 1 FROM runrun_v2.payment_adjustments"
            + " WHERE order_id=? AND status IN ('PENDING_SUPPLEMENT','PENDING_REFUND'))",
        Boolean.class,
        parameter2,
        parameter3);
  }

  public java.lang.Boolean caseBlockedValueFulfillmentCompletionBlocks(java.lang.Long parameter2) {
    return jdbc.queryForObject(
        "SELECT EXISTS(SELECT 1 FROM runrun_v2.fulfillment_completion_blocks WHERE order_id=? AND"
            + " source_type='AFTER_SALES')",
        Boolean.class,
        parameter2);
  }

  public java.lang.Long capacityValueFulfillmentTasks(java.lang.Long parameter2) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM runrun_v2.fulfillment_tasks WHERE courier_id=? AND status='ACTIVE'",
        Long.class,
        parameter2);
  }

  public java.util.List<java.util.Map<java.lang.String, java.lang.Object>> lockRowsAppUsers(
      java.lang.Long parameter1) {
    return jdbc.queryForList(
        "SELECT id FROM runrun_v2.app_users WHERE id=(SELECT user_id FROM runrun_v2.v2_orders WHERE"
            + " id=?) FOR KEY SHARE",
        parameter1);
  }

  public java.util.List<java.lang.String> currentRowsFulfillmentTasks(
      java.lang.Long parameter2, java.lang.Long parameter3) {
    return jdbc.queryForList(
        "SELECT id::text FROM runrun_v2.fulfillment_tasks WHERE courier_id=? AND order_id=? ORDER"
            + " BY id DESC LIMIT 1",
        String.class,
        parameter2,
        parameter3);
  }

  public java.util.List<com.zhangxuan.runrunv2.model.fulfillment.FulfillmentModels.Task>
      taskRowsFulfillmentTasks(java.lang.Long parameter2) {
    return jdbc.query(
        "SELECT * FROM runrun_v2.fulfillment_tasks WHERE id=?",
        (r, n) ->
            new Task(
                r.getString("id"),
                r.getString("order_id"),
                r.getString("courier_id"),
                r.getString("status"),
                r.getLong("version"),
                r.getObject("created_at", OffsetDateTime.class)),
        parameter2);
  }

  public java.lang.Boolean unsupersededValueFulfillmentTasks(java.lang.Long parameter2) {
    return jdbc.queryForObject(
        "SELECT superseded_at IS NULL FROM runrun_v2.fulfillment_tasks WHERE id=?",
        Boolean.class,
        parameter2);
  }

  public java.lang.Boolean proofPermissionValueFulfillmentTasks(
      java.lang.Long parameter2, java.lang.Long parameter3) {
    return jdbc.queryForObject(
        "SELECT id=? FROM runrun_v2.fulfillment_tasks WHERE order_id=? ORDER BY id DESC LIMIT 1",
        Boolean.class,
        parameter2,
        parameter3);
  }

  public java.lang.Long requireProofValueV(
      java.lang.Long parameter2,
      java.lang.Long parameter3,
      java.lang.Long parameter4,
      java.lang.String parameter5) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM runrun_v2.v2_order_proofs WHERE id=? AND order_id=? AND task_id=? AND"
            + " kind=? AND storage_status='AVAILABLE' AND deleted_at IS NULL",
        Long.class,
        parameter2,
        parameter3,
        parameter4,
        parameter5);
  }

  public int supersedeWriteFulfillmentTasks(
      java.time.OffsetDateTime parameter1, java.lang.Long parameter2, java.lang.Long parameter3) {
    return jdbc.update(
        "UPDATE runrun_v2.fulfillment_tasks SET superseded_at=?,version=version+1 WHERE order_id=?"
            + " AND id<>? AND superseded_at IS NULL",
        parameter1,
        parameter2,
        parameter3);
  }

  public int closeOffersWriteFulfillmentTasks(
      java.time.OffsetDateTime parameter1,
      java.lang.Long parameter2,
      java.lang.Long parameter3,
      java.lang.Long parameter4) {
    return jdbc.update(
        "UPDATE runrun_v2.fulfillment_tasks SET status='CANCELLED',ended_at=?,version=version+1"
            + " WHERE order_id=? AND status='OFFERED' AND (?::bigint IS NULL OR id<>?::bigint)",
        parameter1,
        parameter2,
        parameter3,
        parameter4);
  }

  public int endWriteFulfillmentTasks(
      java.lang.String status, java.time.OffsetDateTime parameter2, java.lang.Long parameter3) {
    return jdbc.update(
        "UPDATE runrun_v2.fulfillment_tasks SET status=?,ended_at=?,version=version+1 WHERE id=?",
        status,
        parameter2,
        parameter3);
  }

  public int updateOrderWriteV(
      java.lang.String assignment, java.time.OffsetDateTime parameter1, java.lang.Long parameter2) {
    return jdbc.update(
        "UPDATE runrun_v2.v2_orders SET "
            + assignment
            + ",version=version+1,updated_at=? WHERE id=?",
        parameter1,
        parameter2);
  }

  public com.zhangxuan.runrunv2.model.fulfillment.FulfillmentModels.Order orderValueV(
      java.lang.Long parameter2) {
    return jdbc.queryForObject(
        "SELECT * FROM runrun_v2.v2_orders WHERE id=?",
        (r, n) ->
            new Order(
                r.getString("id"),
                r.getString("order_no"),
                2,
                r.getString("status"),
                r.getString("status"),
                r.getLong("version"),
                r.getLong("original_fee_cents"),
                r.getLong("effective_fee_cents"),
                r.getObject("delivered_at", OffsetDateTime.class),
                r.getObject("confirm_started_at", OffsetDateTime.class),
                r.getObject("confirm_deadline", OffsetDateTime.class)),
        parameter2);
  }

  public java.lang.Boolean displayStatusValuePaymentAdjustments(java.lang.Long parameter2) {
    return jdbc.queryForObject(
        "SELECT EXISTS(SELECT 1 FROM runrun_v2.payment_adjustments WHERE order_id=? AND"
            + " status='PENDING_SUPPLEMENT')",
        Boolean.class,
        parameter2);
  }

  public com.zhangxuan.runrunv2.model.fulfillment.FulfillmentModels.Event
      readEventValueLocationEvents(java.lang.Long parameter2, long version) {
    return jdbc.queryForObject(
        "SELECT * FROM runrun_v2.location_events WHERE id=?",
        (r, n) ->
            new Event(
                r.getString("id"),
                r.getObject("received_at", OffsetDateTime.class),
                r.getBoolean("accepted"),
                version),
        parameter2);
  }

  public int eventWriteV(
      java.lang.Long parameter1,
      java.lang.Long parameter2,
      java.lang.Long parameter3,
      java.lang.String parameter4,
      java.lang.String key,
      java.lang.String parameter6) {
    return jdbc.update(
        "INSERT INTO"
            + " runrun_v2.v2_order_events(order_id,task_id,actor_id,event_type,request_id,payload)"
            + " VALUES(?,?,?,?,?,?::jsonb)",
        parameter1,
        parameter2,
        parameter3,
        parameter4,
        key,
        parameter6);
  }

  public int notifyWriteNotificationOutbox(
      java.lang.Long parameter1, java.lang.String parameter2, java.lang.String parameter3) {
    return jdbc.update(
        "INSERT INTO runrun_v2.notification_outbox(recipient_id,event_key,channel,payload)"
            + " VALUES(?,?,'IN_APP',?::jsonb) ON CONFLICT DO NOTHING",
        parameter1,
        parameter2,
        parameter3);
  }
}
