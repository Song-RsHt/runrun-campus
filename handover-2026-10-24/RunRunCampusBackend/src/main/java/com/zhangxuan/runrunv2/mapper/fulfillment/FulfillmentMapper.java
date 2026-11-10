package com.zhangxuan.runrunv2.mapper.fulfillment;

import com.zhangxuan.runrunv2.config.SingleRowRequired;
import com.zhangxuan.runrunv2.model.fulfillment.LocationEventRow;
import com.zhangxuan.runrunv2.vo.fulfillment.FulfillmentOrderVO;
import com.zhangxuan.runrunv2.vo.fulfillment.FulfillmentTaskVO;
import com.zhangxuan.runrunv2.vo.fulfillment.LocationEventVO;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 履约持久化映射；事务、锁顺序及业务校验由原调用方保持。 */
@Mapper
public interface FulfillmentMapper {

    @SingleRowRequired
    String insertActiveTask(
            @Param("orderId") Long orderId,
            @Param("courierId") Long courierId,
            @Param("acceptedAt") OffsetDateTime acceptedAt,
            @Param("createdAt") OffsetDateTime createdAt);

    List<Map<String, Object>> lockActiveCourier(@Param("courierId") Long courierId);

    @SingleRowRequired
    Long countActiveTasksForInvitation(@Param("orderId") Long orderId);

    @SingleRowRequired
    String findLatestTaskId(@Param("orderId") Long orderId);

    @SingleRowRequired
    String insertTaskOffer(
            @Param("orderId") Long orderId,
            @Param("courierId") Long courierId,
            @Param("sourceTaskId") Long sourceTaskId,
            @Param("createdAt") OffsetDateTime createdAt);

    @SingleRowRequired
    Long countActiveTasksForResponse(@Param("orderId") Long orderId);

    int acceptTaskOffer(
            @Param("acceptedAt") OffsetDateTime acceptedAt, @Param("taskId") Long taskId);

    @SingleRowRequired
    Long countPickupProofs(@Param("taskId") Long taskId);

    @SingleRowRequired
    Long countTaskProofs(@Param("taskId") Long taskId, @Param("kind") String kind);

    @SingleRowRequired
    Long countProofsByStorageKey(@Param("storageKey") String storageKey);

    @SingleRowRequired
    String insertOrderProof(
            @Param("orderId") Long orderId,
            @Param("taskId") Long taskId,
            @Param("uploadedBy") Long uploadedBy,
            @Param("kind") String kind,
            @Param("storageKey") String storageKey);

    List<Map<String, Object>> findLocationRequest(
            @Param("taskId") Long taskId,
            @Param("eventType") String eventType,
            @Param("requestId") String requestId);

    @SingleRowRequired
    Long countAcceptedDeliveryLocations(@Param("taskId") Long taskId);

    @SingleRowRequired
    String insertLocationEvent(
            @Param("orderId") Long orderId,
            @Param("taskId") Long taskId,
            @Param("courierId") Long courierId,
            @Param("eventType") String eventType,
            @Param("latitude") BigDecimal latitude,
            @Param("longitude") BigDecimal longitude,
            @Param("coordinateSystem") String coordinateSystem,
            @Param("receivedAt") OffsetDateTime receivedAt,
            @Param("requestId") String requestId,
            @Param("accepted") boolean accepted,
            @Param("decisionReason") String decisionReason);

    int markTaskPickedUp(
            @Param("pickedUpAt") OffsetDateTime pickedUpAt, @Param("taskId") Long taskId);

    @SingleRowRequired
    Long countValidDeliveryLocations(
            @Param("locationEventId") Long locationEventId,
            @Param("orderId") Long orderId,
            @Param("taskId") Long taskId,
            @Param("actorId") Long actorId);

    int markTaskDelivered(
            @Param("deliveredAt") OffsetDateTime deliveredAt,
            @Param("endedAt") OffsetDateTime endedAt,
            @Param("taskId") Long taskId);

    int markOrderDelivered(
            @Param("deliveredAt") OffsetDateTime deliveredAt,
            @Param("confirmStartedAt") OffsetDateTime confirmStartedAt,
            @Param("confirmDeadline") OffsetDateTime confirmDeadline,
            @Param("updatedAt") OffsetDateTime updatedAt,
            @Param("orderId") Long orderId);

    List<String> findOrdersDueForConfirmation(
            @Param("deadlineCutoff") OffsetDateTime deadlineCutoff);

    int insertCompletionBlock(
            @Param("orderId") Long orderId,
            @Param("sourceType") String sourceType,
            @Param("sourceId") String sourceId);

    int deleteCompletionBlock(
            @Param("orderId") Long orderId,
            @Param("sourceType") String sourceType,
            @Param("sourceId") String sourceId);

    int restartConfirmationWindow(
            @Param("confirmStartedAt") OffsetDateTime confirmStartedAt,
            @Param("confirmDeadline") OffsetDateTime confirmDeadline,
            @Param("updatedAt") OffsetDateTime updatedAt,
            @Param("orderId") Long orderId);

    int markOrderCompleted(
            @Param("completedAt") OffsetDateTime completedAt,
            @Param("updatedAt") OffsetDateTime updatedAt,
            @Param("orderId") Long orderId);

    @SingleRowRequired
    Boolean hasAdjustmentCompletionBlock(
            @Param("blockedOrderId") Long blockedOrderId,
            @Param("adjustmentOrderId") Long adjustmentOrderId);

    @SingleRowRequired
    Boolean hasAfterSalesCompletionBlock(@Param("orderId") Long orderId);

    @SingleRowRequired
    Long countActiveCourierTasks(@Param("courierId") Long courierId);

    List<Map<String, Object>> lockOrderUser(@Param("orderId") Long orderId);

    List<String> findLatestCourierTaskId(
            @Param("courierId") Long courierId, @Param("orderId") Long orderId);

    List<FulfillmentTaskVO> findTask(@Param("taskId") Long taskId);

    @SingleRowRequired
    Boolean isTaskUnsuperseded(@Param("taskId") Long taskId);

    @SingleRowRequired
    Boolean isLatestOrderTask(@Param("taskId") Long taskId, @Param("orderId") Long orderId);

    @SingleRowRequired
    Long countAvailableTaskProofs(
            @Param("proofId") Long proofId,
            @Param("orderId") Long orderId,
            @Param("taskId") Long taskId,
            @Param("kind") String kind);

    int supersedeOtherTasks(
            @Param("supersededAt") OffsetDateTime supersededAt,
            @Param("orderId") Long orderId,
            @Param("retainedTaskId") Long retainedTaskId);

    int cancelOtherOffers(
            @Param("endedAt") OffsetDateTime endedAt,
            @Param("orderId") Long orderId,
            @Param("retainedTaskId") Long retainedTaskId,
            @Param("excludedTaskId") Long excludedTaskId);

    int endTask(
            @Param("status") String status,
            @Param("endedAt") OffsetDateTime endedAt,
            @Param("taskId") Long taskId);

    @SingleRowRequired
    FulfillmentOrderVO findOrder(@Param("orderId") Long orderId);

    @SingleRowRequired
    Boolean hasPendingSupplement(@Param("orderId") Long orderId);

    @SingleRowRequired
    default LocationEventVO findLocationEvent(Long eventId, long orderVersion) {
        LocationEventRow event = readLocationEvent(eventId);
        return new LocationEventVO(event.id(), event.receivedAt(), event.accepted(), orderVersion);
    }

    @SingleRowRequired
    LocationEventRow readLocationEvent(@Param("eventId") Long eventId);

    int insertOrderEvent(
            @Param("orderId") Long orderId,
            @Param("taskId") Long taskId,
            @Param("actorId") Long actorId,
            @Param("eventType") String eventType,
            @Param("requestId") String requestId,
            @Param("payload") String payload);

    int insertNotificationIfAbsent(
            @Param("recipientId") Long recipientId,
            @Param("eventKey") String eventKey,
            @Param("payload") String payload);

    int markOrderAccepted(
            @Param("updatedAt") OffsetDateTime updatedAt, @Param("orderId") Long orderId);

    int clearOrderConfirmation(
            @Param("updatedAt") OffsetDateTime updatedAt, @Param("orderId") Long orderId);

    int acceptOrderAndClearConfirmation(
            @Param("updatedAt") OffsetDateTime updatedAt, @Param("orderId") Long orderId);

    int markOrderPendingAssignment(
            @Param("updatedAt") OffsetDateTime updatedAt, @Param("orderId") Long orderId);

    int advanceOrderVersion(
            @Param("updatedAt") OffsetDateTime updatedAt, @Param("orderId") Long orderId);

    int markOrderPickedUp(
            @Param("updatedAt") OffsetDateTime updatedAt, @Param("orderId") Long orderId);
}
