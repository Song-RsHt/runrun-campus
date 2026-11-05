package com.zhangxuan.runrunv2.model.payment;
import com.zhangxuan.runrunv2.model.order.OrderModels;

import java.time.OffsetDateTime;
import java.util.List;

public final class PaymentModels {
    private PaymentModels() {}
    public enum Result { SUCCESS, PROCESSING, FAILED, UNKNOWN }
    public record VersionRequest(Long expectedVersion) {}
    public record CancelRequest(Long expectedVersion, String reason) {}
    public record ResultRequest(Long expectedVersion, Result result, String eventId) {}
    public record Payment(String id, String orderId, String adjustmentId, String purpose, long amountCents,
                          String status, OffsetDateTime deadline, OffsetDateTime paidAt, long version) {}
    public record Attempt(String id, String paymentOrderId, int attemptNo, String status, long version) {}
    public record Refund(String id, String orderId, String paymentOrderId, String adjustmentId, long amountCents,
                         String purpose, String status, OffsetDateTime succeededAt, long version) {}
    public record Summary(Payment payment, List<Attempt> attempts, List<Refund> refunds) {}
    public record Started(Payment payment, Attempt attempt) {}
    public record Cancelled(OrderModels.Order order, List<Refund> refunds) {}
    public record AdjustmentFunds(String adjustmentId, Payment payment, List<Refund> refunds) {}
}
