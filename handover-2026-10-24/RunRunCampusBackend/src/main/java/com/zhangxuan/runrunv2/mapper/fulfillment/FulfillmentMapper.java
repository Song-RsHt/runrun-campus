package com.zhangxuan.runrunv2.mapper.fulfillment;

import static com.zhangxuan.runrunv2.dto.response.fulfillment.FulfillmentModels.*;

import java.math.BigDecimal;
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

  public String insertActiveTask(
      Long orderId, Long courierId, OffsetDateTime acceptedAt, OffsetDateTime createdAt) {
    return jdbc.queryForObject(
        """
        INSERT INTO runrun_v2.fulfillment_tasks (
            order_id,
            courier_id,
            status,
            accepted_at,
            created_at
        )
        VALUES (
            ?,
            ?,
            'ACTIVE',
            ?,
            ?
        )
        RETURNING id::text
        """,
        String.class,
        orderId,
        courierId,
        acceptedAt,
        createdAt);
  }

  public List<Map<String, Object>> lockActiveCourier(Long courierId) {
    return jdbc.queryForList(
        """
        SELECT id
        FROM runrun_v2.app_users
        WHERE id = ?
            AND role = 'COURIER'
            AND status = 'ACTIVE'
        FOR KEY SHARE
        """,
        courierId);
  }

  public Long countActiveTasksForInvitation(Long orderId) {
    return jdbc.queryForObject(
        """
        SELECT count(*)
        FROM runrun_v2.fulfillment_tasks
        WHERE order_id = ?
            AND status = 'ACTIVE'
        """,
        Long.class,
        orderId);
  }

  public String findLatestTaskId(Long orderId) {
    return jdbc.queryForObject(
        """
        SELECT max(id)::text
        FROM runrun_v2.fulfillment_tasks
        WHERE order_id = ?
        """,
        String.class,
        orderId);
  }

  public String insertTaskOffer(
      Long orderId, Long courierId, Long sourceTaskId, OffsetDateTime createdAt) {
    return jdbc.queryForObject(
        """
        INSERT INTO runrun_v2.fulfillment_tasks (
            order_id,
            courier_id,
            status,
            source_task_id,
            created_at
        )
        VALUES (
            ?,
            ?,
            'OFFERED',
            ?,
            ?
        )
        RETURNING id::text
        """,
        String.class,
        orderId,
        courierId,
        sourceTaskId,
        createdAt);
  }

  public Long countActiveTasksForResponse(Long orderId) {
    return jdbc.queryForObject(
        """
        SELECT count(*)
        FROM runrun_v2.fulfillment_tasks
        WHERE order_id = ?
            AND status = 'ACTIVE'
        """,
        Long.class,
        orderId);
  }

  public int acceptTaskOffer(OffsetDateTime acceptedAt, Long taskId) {
    return jdbc.update(
        """
        UPDATE runrun_v2.fulfillment_tasks
        SET status = 'ACTIVE',
            accepted_at = ?,
            version = version + 1
        WHERE id = ?
        """,
        acceptedAt,
        taskId);
  }

  public Long countPickupProofs(Long taskId) {
    return jdbc.queryForObject(
        """
        SELECT count(*)
        FROM runrun_v2.v2_order_proofs
        WHERE task_id = ?
            AND kind = 'PICKUP'
        """,
        Long.class,
        taskId);
  }

  public Long countTaskProofs(Long taskId, String kind) {
    return jdbc.queryForObject(
        """
        SELECT count(*)
        FROM runrun_v2.v2_order_proofs
        WHERE task_id = ?
            AND kind = ?
        """,
        Long.class,
        taskId,
        kind);
  }

  public Long countProofsByStorageKey(String storageKey) {
    return jdbc.queryForObject(
        """
        SELECT count(*)
        FROM runrun_v2.v2_order_proofs
        WHERE storage_key = ?
        """,
        Long.class,
        storageKey);
  }

  public String insertOrderProof(
      Long orderId, Long taskId, Long uploadedBy, String kind, String storageKey) {
    return jdbc.queryForObject(
        """
        INSERT INTO runrun_v2.v2_order_proofs (
            order_id,
            task_id,
            uploaded_by,
            kind,
            storage_key,
            storage_status
        )
        VALUES (
            ?,
            ?,
            ?,
            ?,
            ?,
            'AVAILABLE'
        )
        RETURNING id::text
        """,
        String.class,
        orderId,
        taskId,
        uploadedBy,
        kind,
        storageKey);
  }

  public List<Map<String, Object>> findLocationRequest(
      Long taskId, String eventType, String requestId) {
    return jdbc.queryForList(
        """
        SELECT *
        FROM runrun_v2.location_events
        WHERE task_id = ?
            AND event_type = ?
            AND request_id = ?
        """,
        taskId,
        eventType,
        requestId);
  }

  public Long countAcceptedDeliveryLocations(Long taskId) {
    return jdbc.queryForObject(
        """
        SELECT count(*)
        FROM runrun_v2.location_events
        WHERE task_id = ?
            AND event_type = 'DELIVERY'
            AND accepted
        """,
        Long.class,
        taskId);
  }

  public String insertLocationEvent(
      Long orderId,
      Long taskId,
      Long courierId,
      String eventType,
      BigDecimal latitude,
      BigDecimal longitude,
      String coordinateSystem,
      OffsetDateTime receivedAt,
      String requestId,
      boolean accepted,
      String decisionReason) {
    return jdbc.queryForObject(
        """
        INSERT INTO runrun_v2.location_events (
            order_id,
            task_id,
            courier_id,
            event_type,
            latitude,
            longitude,
            coordinate_system,
            received_at,
            request_id,
            accepted,
            decision_reason
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
            ?,
            ?,
            ?
        )
        RETURNING id::text
        """,
        String.class,
        orderId,
        taskId,
        courierId,
        eventType,
        latitude,
        longitude,
        coordinateSystem,
        receivedAt,
        requestId,
        accepted,
        decisionReason);
  }

  public int markTaskPickedUp(OffsetDateTime pickedUpAt, Long taskId) {
    return jdbc.update(
        """
        UPDATE runrun_v2.fulfillment_tasks
        SET picked_up_at = ?,
            version = version + 1
        WHERE id = ?
        """,
        pickedUpAt,
        taskId);
  }

  public Long countValidDeliveryLocations(
      Long locationEventId, Long orderId, Long taskId, Long actorId) {
    return jdbc.queryForObject(
        """
        SELECT count(*)
        FROM runrun_v2.location_events
        WHERE id = ?
            AND order_id = ?
            AND task_id = ?
            AND courier_id = ?
            AND event_type = 'DELIVERY'
            AND accepted
            AND deleted_at IS NULL
        """,
        Long.class,
        locationEventId,
        orderId,
        taskId,
        actorId);
  }

  public int markTaskDelivered(OffsetDateTime deliveredAt, OffsetDateTime endedAt, Long taskId) {
    return jdbc.update(
        """
        UPDATE runrun_v2.fulfillment_tasks
        SET status = 'DELIVERED',
            delivered_at = ?,
            ended_at = ?,
            version = version + 1
        WHERE id = ?
        """,
        deliveredAt,
        endedAt,
        taskId);
  }

  public int markOrderDelivered(
      OffsetDateTime deliveredAt,
      OffsetDateTime confirmStartedAt,
      OffsetDateTime confirmDeadline,
      OffsetDateTime updatedAt,
      Long orderId) {
    return jdbc.update(
        """
        UPDATE runrun_v2.v2_orders
        SET status = 'DELIVERED_PENDING_CONFIRMATION',
            delivered_at = coalesce(delivered_at, ?),
            confirm_started_at = ?,
            confirm_deadline = ?,
            version = version + 1,
            updated_at = ?
        WHERE id = ?
        """,
        deliveredAt,
        confirmStartedAt,
        confirmDeadline,
        updatedAt,
        orderId);
  }

  public List<String> findOrdersDueForConfirmation(OffsetDateTime deadlineCutoff) {
    return jdbc.queryForList(
        """
        SELECT id::text
        FROM runrun_v2.v2_orders
        WHERE status = 'DELIVERED_PENDING_CONFIRMATION'
            AND confirm_deadline <= ?
        ORDER BY id
        LIMIT 100
        """,
        String.class,
        deadlineCutoff);
  }

  public int insertCompletionBlock(Long orderId, String sourceType, String sourceId) {
    return jdbc.update(
        """
        INSERT INTO runrun_v2.fulfillment_completion_blocks
        VALUES (
            ?,
            ?,
            ?
        )
        ON CONFLICT DO NOTHING
        """,
        orderId,
        sourceType,
        sourceId);
  }

  public int deleteCompletionBlock(Long orderId, String sourceType, String sourceId) {
    return jdbc.update(
        """
        DELETE
        FROM runrun_v2.fulfillment_completion_blocks
        WHERE order_id = ?
            AND source_type = ?
            AND source_id = ?
        """,
        orderId,
        sourceType,
        sourceId);
  }

  public int restartConfirmationWindow(
      OffsetDateTime confirmStartedAt,
      OffsetDateTime confirmDeadline,
      OffsetDateTime updatedAt,
      Long orderId) {
    return jdbc.update(
        """
        UPDATE runrun_v2.v2_orders
        SET confirm_started_at = ?,
            confirm_deadline = ?,
            version = version + 1,
            updated_at = ?
        WHERE id = ?
        """,
        confirmStartedAt,
        confirmDeadline,
        updatedAt,
        orderId);
  }

  public int markOrderCompleted(
      OffsetDateTime completedAt, OffsetDateTime updatedAt, Long orderId) {
    return jdbc.update(
        """
        UPDATE runrun_v2.v2_orders
        SET status = 'COMPLETED',
            completed_at = ?,
            version = version + 1,
            updated_at = ?
        WHERE id = ?
        """,
        completedAt,
        updatedAt,
        orderId);
  }

  public Boolean hasAdjustmentCompletionBlock(Long blockedOrderId, Long adjustmentOrderId) {
    return jdbc.queryForObject(
        """
        SELECT EXISTS (
            SELECT 1
            FROM runrun_v2.fulfillment_completion_blocks
            WHERE order_id = ?
                AND source_type = 'ADJUSTMENT')
            OR EXISTS (
            SELECT 1
            FROM runrun_v2.payment_adjustments
            WHERE order_id = ?
                AND status IN ('PENDING_SUPPLEMENT', 'PENDING_REFUND'))
        """,
        Boolean.class,
        blockedOrderId,
        adjustmentOrderId);
  }

  public Boolean hasAfterSalesCompletionBlock(Long orderId) {
    return jdbc.queryForObject(
        """
        SELECT EXISTS (
            SELECT 1
            FROM runrun_v2.fulfillment_completion_blocks
            WHERE order_id = ?
                AND source_type = 'AFTER_SALES')
        """,
        Boolean.class,
        orderId);
  }

  public Long countActiveCourierTasks(Long courierId) {
    return jdbc.queryForObject(
        """
        SELECT count(*)
        FROM runrun_v2.fulfillment_tasks
        WHERE courier_id = ?
            AND status = 'ACTIVE'
        """,
        Long.class,
        courierId);
  }

  public List<Map<String, Object>> lockOrderUser(Long orderId) {
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

  public List<String> findLatestCourierTaskId(Long courierId, Long orderId) {
    return jdbc.queryForList(
        """
        SELECT id::text
        FROM runrun_v2.fulfillment_tasks
        WHERE courier_id = ?
            AND order_id = ?
        ORDER BY id DESC
        LIMIT 1
        """,
        String.class,
        courierId,
        orderId);
  }

  public List<Task> findTask(Long taskId) {
    return jdbc.query(
        """
        SELECT *
        FROM runrun_v2.fulfillment_tasks
        WHERE id = ?
        """,
        (r, n) ->
            new Task(
                r.getString("id"),
                r.getString("order_id"),
                r.getString("courier_id"),
                r.getString("status"),
                r.getLong("version"),
                r.getObject("created_at", OffsetDateTime.class)),
        taskId);
  }

  public Boolean isTaskUnsuperseded(Long taskId) {
    return jdbc.queryForObject(
        """
        SELECT superseded_at IS NULL
        FROM runrun_v2.fulfillment_tasks
        WHERE id = ?
        """,
        Boolean.class,
        taskId);
  }

  public Boolean isLatestOrderTask(Long taskId, Long orderId) {
    return jdbc.queryForObject(
        """
        SELECT id = ?
        FROM runrun_v2.fulfillment_tasks
        WHERE order_id = ?
        ORDER BY id DESC
        LIMIT 1
        """,
        Boolean.class,
        taskId,
        orderId);
  }

  public Long countAvailableTaskProofs(Long proofId, Long orderId, Long taskId, String kind) {
    return jdbc.queryForObject(
        """
        SELECT count(*)
        FROM runrun_v2.v2_order_proofs
        WHERE id = ?
            AND order_id = ?
            AND task_id = ?
            AND kind = ?
            AND storage_status = 'AVAILABLE'
            AND deleted_at IS NULL
        """,
        Long.class,
        proofId,
        orderId,
        taskId,
        kind);
  }

  public int supersedeOtherTasks(OffsetDateTime supersededAt, Long orderId, Long retainedTaskId) {
    return jdbc.update(
        """
        UPDATE runrun_v2.fulfillment_tasks
        SET superseded_at = ?,
            version = version + 1
        WHERE order_id = ?
            AND id <> ?
            AND superseded_at IS NULL
        """,
        supersededAt,
        orderId,
        retainedTaskId);
  }

  public int cancelOtherOffers(
      OffsetDateTime endedAt, Long orderId, Long retainedTaskId, Long excludedTaskId) {
    return jdbc.update(
        """
        UPDATE runrun_v2.fulfillment_tasks
        SET status = 'CANCELLED',
            ended_at = ?,
            version = version + 1
        WHERE order_id = ?
            AND status = 'OFFERED'
            AND (?::bigint IS NULL
                OR id <> ?::bigint)
        """,
        endedAt,
        orderId,
        retainedTaskId,
        excludedTaskId);
  }

  public int endTask(String status, OffsetDateTime endedAt, Long taskId) {
    return jdbc.update(
        """
        UPDATE runrun_v2.fulfillment_tasks
        SET status = ?,
            ended_at = ?,
            version = version + 1
        WHERE id = ?
        """,
        status,
        endedAt,
        taskId);
  }

  private int updateOrder(String assignment, OffsetDateTime updatedAt, Long orderId) {
    return jdbc.update(
        """
        UPDATE runrun_v2.v2_orders
        SET
        """
            + assignment
            + """
              \s,
                  version = version + 1,
                  updated_at = ?
              WHERE id = ?
              """,
        updatedAt,
        orderId);
  }

  public Order findOrder(Long orderId) {
    return jdbc.queryForObject(
        """
        SELECT *
        FROM runrun_v2.v2_orders
        WHERE id = ?
        """,
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
        orderId);
  }

  public Boolean hasPendingSupplement(Long orderId) {
    return jdbc.queryForObject(
        """
        SELECT EXISTS (
            SELECT 1
            FROM runrun_v2.payment_adjustments
            WHERE order_id = ?
                AND status = 'PENDING_SUPPLEMENT')
        """,
        Boolean.class,
        orderId);
  }

  public Event findLocationEvent(Long eventId, long orderVersion) {
    return jdbc.queryForObject(
        """
        SELECT *
        FROM runrun_v2.location_events
        WHERE id = ?
        """,
        (r, n) ->
            new Event(
                r.getString("id"),
                r.getObject("received_at", OffsetDateTime.class),
                r.getBoolean("accepted"),
                orderVersion),
        eventId);
  }

  public int insertOrderEvent(
      Long orderId, Long taskId, Long actorId, String eventType, String requestId, String payload) {
    return jdbc.update(
        """
        INSERT INTO runrun_v2.v2_order_events (
            order_id,
            task_id,
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
            ?,
            ?::jsonb
        )
        """,
        orderId,
        taskId,
        actorId,
        eventType,
        requestId,
        payload);
  }

  public int insertNotificationIfAbsent(Long recipientId, String eventKey, String payload) {
    return jdbc.update(
        """
        INSERT INTO runrun_v2.notification_outbox (
            recipient_id,
            event_key,
            channel,
            payload
        )
        VALUES (
            ?,
            ?,
            'IN_APP',
            ?::jsonb
        )
        ON CONFLICT DO NOTHING
        """,
        recipientId,
        eventKey,
        payload);
  }

  public int markOrderAccepted(OffsetDateTime updatedAt, Long orderId) {
    return updateOrder("status='ACCEPTED'", updatedAt, orderId);
  }

  public int clearOrderConfirmation(OffsetDateTime updatedAt, Long orderId) {
    return updateOrder("confirm_started_at=NULL,confirm_deadline=NULL", updatedAt, orderId);
  }

  public int acceptOrderAndClearConfirmation(OffsetDateTime updatedAt, Long orderId) {
    return updateOrder(
        "status='ACCEPTED',confirm_started_at=NULL,confirm_deadline=NULL", updatedAt, orderId);
  }

  public int markOrderPendingAssignment(OffsetDateTime updatedAt, Long orderId) {
    return updateOrder("status='PENDING_ASSIGNMENT'", updatedAt, orderId);
  }

  public int advanceOrderVersion(OffsetDateTime updatedAt, Long orderId) {
    return updateOrder("status=status", updatedAt, orderId);
  }

  public int markOrderPickedUp(OffsetDateTime updatedAt, Long orderId) {
    return updateOrder("status='PICKED_UP'", updatedAt, orderId);
  }
}
