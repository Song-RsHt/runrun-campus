package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.auth.AuthService;
import com.zhangxuan.runruncampus.order.OrderIssueType;
import com.zhangxuan.runruncampus.order.OrderPaymentMethod;
import com.zhangxuan.runruncampus.order.OrderPaymentStatus;
import com.zhangxuan.runruncampus.order.OrderProofType;
import com.zhangxuan.runruncampus.order.OrderStatus;
import com.zhangxuan.runruncampus.order.ServiceType;
import com.zhangxuan.runruncampus.order.ManualPaymentStatus;
import com.zhangxuan.runruncampus.order.CourierSettlementStatus;
import com.zhangxuan.runruncampus.payment.ManualQrPaymentService;
import com.zhangxuan.runruncampus.user.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.Instant;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {
    private final AuthService authService;
    private final AdminOrderService adminOrderService;
    private final ManualQrPaymentService manualQrPaymentService;

    public AdminOrderController(AuthService authService, AdminOrderService adminOrderService,
                                ManualQrPaymentService manualQrPaymentService) {
        this.authService = authService;
        this.adminOrderService = adminOrderService;
        this.manualQrPaymentService = manualQrPaymentService;
    }

    @GetMapping
    AdminOrderPageResponse list(@RequestHeader("Authorization") String authorization,
                                @RequestParam(required = false) String query,
                                @RequestParam(required = false) OrderStatus status,
                                @RequestParam(defaultValue = "false") boolean archived,
                                @RequestParam(defaultValue = "0") @Min(0) int page,
                                @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        authService.requirePermission(authorization, AdminPermission.ORDER_VIEW);
        return adminOrderService.list(query, status, archived, page, size);
    }

    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    ResponseEntity<byte[]> export(@RequestHeader("Authorization") String authorization,
                                  @RequestParam(required = false) String query,
                                  @RequestParam(required = false) OrderStatus status,
                                  @RequestParam(defaultValue = "false") boolean archived) {
        authService.requirePermission(authorization, AdminPermission.ORDER_EXPORT);
        AdminOrderService.OrderExport export = adminOrderService.exportCsv(query, status, archived);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=orders.csv")
                .header("X-Export-Limit", String.valueOf(AdminOrderService.EXPORT_LIMIT))
                .header("X-Export-Truncated", String.valueOf(export.truncated()))
                .body(export.content());
    }

    @GetMapping("/{id}")
    AdminOrderDetailResponse detail(@RequestHeader("Authorization") String authorization,
                                    @PathVariable Long id) {
        authService.requirePermission(authorization, AdminPermission.ORDER_VIEW);
        return adminOrderService.detail(id);
    }

    @PostMapping("/{id}/manual-payment/confirm")
    ManualQrPaymentService.ManualPaymentResponse confirmManualPayment(
            @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        return manualQrPaymentService.confirmPayment(
                authService.requirePermission(authorization, AdminPermission.PAYMENT_REVIEW), id);
    }

    @PostMapping("/{id}/manual-payment/reject")
    ManualQrPaymentService.ManualPaymentResponse rejectManualPayment(
            @RequestHeader("Authorization") String authorization, @PathVariable Long id,
            @Valid @RequestBody RejectManualPaymentRequest request) {
        return manualQrPaymentService.rejectPayment(
                authService.requirePermission(authorization, AdminPermission.PAYMENT_REVIEW), id, request.reason());
    }

    @PostMapping("/{id}/courier-settlement/confirm")
    ManualQrPaymentService.ManualPaymentResponse confirmCourierSettlement(
            @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        return manualQrPaymentService.confirmCourierSettlement(
                authService.requirePermission(authorization, AdminPermission.PAYMENT_REVIEW), id);
    }

    public record AdminOrderPageResponse(
            List<AdminOrderListItem> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) { }

    public record AdminOrderListItem(
            Long id,
            String orderNo,
            OrderStatus status,
            ServiceType serviceType,
            String merchant,
            String destination,
            int feeCents,
            OrderPaymentMethod paymentMethod,
            OrderPaymentStatus paymentStatus,
            int paidCents,
            ManualPaymentStatus manualPaymentStatus,
            CourierSettlementStatus courierSettlementStatus,
            UserSummary customer,
            UserSummary courier,
            Instant createdAt,
            Instant updatedAt,
            Instant archivedAt
    ) { }

    public record AdminOrderDetailResponse(
            AdminOrderListItem order,
            String pickupCode,
            String pickupLocation,
            int originalFeeCents,
            int discountCents,
            String couponTitle,
            boolean membershipUsed,
            String membershipPlanTitle,
            Integer ratingScore,
            String ratingComment,
            Instant ratedAt,
            Double courierLatitude,
            Double courierLongitude,
            Instant courierLocationUpdatedAt,
            String payerReference,
            Instant paymentRequestedAt,
            Instant paymentSubmittedAt,
            Instant paymentReviewedAt,
            String paymentReviewerName,
            String paymentReviewNote,
            Instant courierSettledAt,
            String courierSettlementReviewerName,
            List<OrderEventItem> events,
            List<OrderIssueItem> issues,
            List<OrderProofItem> proofs
    ) { }

    public record UserSummary(Long id, String displayName, String phone, UserRole role) { }

    public record OrderEventItem(Long id, OrderStatus status, UserSummary actor, Instant createdAt) { }

    public record OrderIssueItem(
            Long id,
            OrderIssueType type,
            String description,
            UserSummary reporter,
            Instant createdAt
    ) { }

    public record OrderProofItem(
            Long id,
            OrderProofType type,
            String originalFileName,
            String contentType,
            long sizeBytes,
            UserSummary uploader,
            Instant createdAt
    ) { }

    public record RejectManualPaymentRequest(
            @NotBlank(message = "请输入驳回原因")
            @Size(max = 200, message = "驳回原因不能超过200字") String reason
    ) { }
}
