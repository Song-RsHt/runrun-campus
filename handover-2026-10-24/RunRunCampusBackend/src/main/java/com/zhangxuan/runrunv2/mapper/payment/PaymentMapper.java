package com.zhangxuan.runrunv2.mapper.payment;

import com.zhangxuan.runrunv2.config.SingleRowRequired;
import com.zhangxuan.runrunv2.vo.payment.PaymentAttemptVO;
import com.zhangxuan.runrunv2.vo.payment.PaymentOrderVO;
import com.zhangxuan.runrunv2.vo.payment.RefundOrderVO;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 支付与退款持久化映射；事务、锁顺序及业务校验由原调用方保持。 */
@Mapper
public interface PaymentMapper {

    @SingleRowRequired
    String insertOriginalPayment(
            @Param("orderId") Long orderId,
            @Param("amountCents") long amountCents,
            @Param("deadline") OffsetDateTime deadline);

    @SingleRowRequired
    Integer findNextAttemptNumber(@Param("paymentOrderId") Long paymentOrderId);

    @SingleRowRequired
    String insertPaymentAttempt(
            @Param("paymentOrderId") Long paymentOrderId,
            @Param("attemptNo") int attemptNo,
            @Param("channelReference") String channelReference);

    int cancelPendingAdjustments(
            @Param("resolvedAt") OffsetDateTime resolvedAt, @Param("orderId") Long orderId);

    int markRefundProcessing(@Param("refundId") Long refundId);

    @SingleRowRequired
    Long countAdjustmentById(@Param("adjustmentId") Long adjustmentId);

    @SingleRowRequired
    Long countPendingAdjustments(@Param("orderId") Long orderId);

    int insertApprovedAdjustment(
            @Param("adjustmentId") Long adjustmentId,
            @Param("orderId") Long orderId,
            @Param("deltaCents") long deltaCents,
            @Param("status") String status,
            @Param("approvedBy") Long approvedBy,
            @Param("reason") String reason,
            @Param("approvedAt") OffsetDateTime approvedAt);

    int updateEffectiveFeeAndClearConfirmation(
            @Param("effectiveFeeCents") long effectiveFeeCents,
            @Param("updatedAt") OffsetDateTime updatedAt,
            @Param("orderId") Long orderId);

    @SingleRowRequired
    String insertSupplementPayment(
            @Param("orderId") Long orderId,
            @Param("adjustmentId") Long adjustmentId,
            @Param("amountCents") long amountCents,
            @Param("deadline") OffsetDateTime deadline);

    List<String> findExpiredUnpaidOrderIds(@Param("deadlineCutoff") OffsetDateTime deadlineCutoff);

    int closeUnpaidOriginalPayments(
            @Param("updatedAt") OffsetDateTime updatedAt, @Param("orderId") Long orderId);

    int updateAttemptResult(
            @Param("status") String status,
            @Param("resultReceivedAt") OffsetDateTime resultReceivedAt,
            @Param("attemptId") Long attemptId);

    int markPaymentPaid(
            @Param("paidAt") OffsetDateTime paidAt,
            @Param("creditedAttemptId") Long creditedAttemptId,
            @Param("updatedAt") OffsetDateTime updatedAt,
            @Param("paymentOrderId") Long paymentOrderId);

    int markOrderPendingAssignment(
            @Param("updatedAt") OffsetDateTime updatedAt, @Param("orderId") Long orderId);

    int updateRefundResult(
            @Param("status") String status,
            @Param("succeededAt") OffsetDateTime succeededAt,
            @Param("refundId") Long refundId);

    @SingleRowRequired
    Long countUnfinishedAdjustmentRefunds(@Param("adjustmentId") Long adjustmentId);

    List<String> findRefundIdsByBusinessKey(@Param("businessKey") String businessKey);

    @SingleRowRequired
    String insertRefund(
            @Param("orderId") Long orderId,
            @Param("paymentOrderId") Long paymentOrderId,
            @Param("sourceAttemptId") Long sourceAttemptId,
            @Param("adjustmentId") Long adjustmentId,
            @Param("amountCents") long amountCents,
            @Param("purpose") String purpose,
            @Param("reason") String reason,
            @Param("businessKey") String businessKey,
            @Param("channelReference") String channelReference);

    int completePendingAdjustment(
            @Param("resolvedAt") OffsetDateTime resolvedAt,
            @Param("adjustmentId") Long adjustmentId);

    @SingleRowRequired
    String findAdjustmentOrderId(@Param("adjustmentId") Long adjustmentId);

    @SingleRowRequired
    Boolean isSupplementPending(@Param("adjustmentId") Long adjustmentId);

    @SingleRowRequired
    Long sumReservedRefundCents(@Param("sourceAttemptId") Long sourceAttemptId);

    @SingleRowRequired
    String findCreditedAttemptId(@Param("paymentOrderId") Long paymentOrderId);

    int cancelOrderAndClearConfirmation(
            @Param("cancelledAt") OffsetDateTime cancelledAt,
            @Param("updatedAt") OffsetDateTime updatedAt,
            @Param("orderId") Long orderId);

    List<Map<String, Object>> lockOrderNotificationRecipient(@Param("orderId") long orderId);

    int updatePaymentStatusIfDifferent(
            @Param("status") String status,
            @Param("updatedAt") OffsetDateTime updatedAt,
            @Param("paymentOrderId") Long paymentOrderId,
            @Param("excludedStatus") String excludedStatus);

    /** 读取并锁定 runrun_v2.payment_orders 的匹配行；锁持有至调用方事务结束。 */
    List<PaymentOrderVO> findPayment(
            @Param("lock") boolean lock, @Param("paymentOrderId") long paymentOrderId);

    List<PaymentAttemptVO> findAttempt(@Param("attemptId") long attemptId);

    List<RefundOrderVO> findRefund(@Param("refundId") long refundId);

    List<String> findAttemptIds(@Param("paymentOrderId") Long paymentOrderId);

    List<String> findPaymentIdsByOrder(@Param("orderId") Long orderId);

    List<String> findRefundIdsByOrder(@Param("orderId") Long orderId);

    int insertResultEvent(
            @Param("eventId") String eventId,
            @Param("resourceType") String resourceType,
            @Param("resourceId") Long resourceId,
            @Param("result") String result);

    List<Object> lockResultEvent(@Param("eventId") String eventId);

    List<Map<String, Object>> findResultEvent(@Param("eventId") String eventId);

    @SingleRowRequired
    String findRefundSourceAttemptId(@Param("refundId") Long refundId);

    @SingleRowRequired
    Long sumSucceededRefundCents(@Param("sourceAttemptId") Long sourceAttemptId);

    @SingleRowRequired
    Long sumRecordedPlatformRefundCents(@Param("sourceAttemptId") String sourceAttemptId);

    int insertRefundFinancialEvent(
            @Param("orderId") Long orderId,
            @Param("eventKey") String eventKey,
            @Param("kind") String kind,
            @Param("amountCents") long amountCents,
            @Param("platformCents") long platformCents,
            @Param("courierCents") long courierCents,
            @Param("payload") String payload);

    int insertOrderEventIfAbsent(
            @Param("orderId") Long orderId,
            @Param("actorId") Long actorId,
            @Param("eventType") String eventType,
            @Param("requestId") String requestId,
            @Param("payload") String payload);

    int insertFinancialEventIfAbsent(
            @Param("orderId") Long orderId,
            @Param("eventKey") String eventKey,
            @Param("kind") String kind,
            @Param("amountCents") long amountCents,
            @Param("platformCents") long platformCents,
            @Param("courierCents") long courierCents,
            @Param("payload") String payload);

    int insertOrderNotificationIfAbsent(
            @Param("eventKey") String eventKey,
            @Param("payload") String payload,
            @Param("orderId") Long orderId);

    int insertAdminAudit(
            @Param("actorId") Long actorId,
            @Param("action") String action,
            @Param("resourceId") Long resourceId,
            @Param("requestId") String requestId,
            @Param("reason") String reason);
}
