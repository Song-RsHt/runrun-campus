package com.zhangxuan.runruncampus.controller.order;

import com.zhangxuan.runruncampus.enums.order.CourierSettlementStatus;
import com.zhangxuan.runruncampus.enums.order.DeliveryZone;
import com.zhangxuan.runruncampus.enums.order.ManualPaymentStatus;
import com.zhangxuan.runruncampus.enums.order.OrderIssueType;
import com.zhangxuan.runruncampus.enums.order.OrderPaymentMethod;
import com.zhangxuan.runruncampus.enums.order.OrderPaymentStatus;
import com.zhangxuan.runruncampus.enums.order.OrderProofType;
import com.zhangxuan.runruncampus.enums.order.OrderStatus;
import com.zhangxuan.runruncampus.enums.order.PickupStation;
import com.zhangxuan.runruncampus.enums.order.ServiceType;
import com.zhangxuan.runruncampus.service.auth.AuthService;
import com.zhangxuan.runruncampus.service.order.CourierReassignmentService;
import com.zhangxuan.runruncampus.service.order.OrderChatService;
import com.zhangxuan.runruncampus.service.order.OrderIssueService;
import com.zhangxuan.runruncampus.service.order.OrderProofService;
import com.zhangxuan.runruncampus.service.order.OrderService;
import com.zhangxuan.runruncampus.service.payment.ManualQrPaymentService;
import com.zhangxuan.runruncampus.service.payment.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
  private final AuthService authService;
  private final OrderService orderService;
  private final OrderProofService proofService;
  private final OrderIssueService issueService;
  private final CourierReassignmentService reassignmentService;
  private final OrderChatService chatService;
  private final ManualQrPaymentService manualQrPaymentService;

  public OrderController(
      AuthService authService,
      OrderService orderService,
      OrderProofService proofService,
      OrderIssueService issueService,
      CourierReassignmentService reassignmentService,
      OrderChatService chatService,
      ManualQrPaymentService manualQrPaymentService) {
    this.authService = authService;
    this.orderService = orderService;
    this.proofService = proofService;
    this.issueService = issueService;
    this.reassignmentService = reassignmentService;
    this.chatService = chatService;
    this.manualQrPaymentService = manualQrPaymentService;
  }

  @PostMapping
  public OrderResponse create(
      @RequestHeader("Authorization") String authorization,
      @RequestHeader(value = "Idempotency-Key", required = false) UUID requestKey,
      @Valid @RequestBody CreateOrderRequest request) {
    return orderService.create(authService.requireUser(authorization), request, requestKey);
  }

  @GetMapping("/mine")
  public List<OrderResponse> mine(@RequestHeader("Authorization") String authorization) {
    return orderService.mine(authService.requireUser(authorization));
  }

  @GetMapping("/mine/active")
  public List<OrderResponse> mineActive(@RequestHeader("Authorization") String authorization) {
    return orderService.mineActive(authService.requireUser(authorization));
  }

  @GetMapping("/mine/history")
  public MineOrderPage mineHistory(
      @RequestHeader("Authorization") String authorization,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) OrderStatus status,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate startDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate endDate) {
    return orderService.mineHistory(
        authService.requireUser(authorization), page, size, status, startDate, endDate);
  }

  @GetMapping("/available")
  public List<OrderResponse> available(@RequestHeader("Authorization") String authorization) {
    return orderService.available(authService.requireUser(authorization));
  }

  @GetMapping("/{id}")
  public OrderResponse get(
      @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
    return orderService.get(authService.requireUser(authorization), id);
  }

  @GetMapping("/{id}/events")
  public List<OrderEventResponse> events(
      @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
    return orderService.events(authService.requireUser(authorization), id);
  }

  @GetMapping("/{id}/location")
  public CourierLocationResponse location(
      @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
    return orderService.location(authService.requireUser(authorization), id);
  }

  @PostMapping("/{id}/location")
  public CourierLocationResponse updateLocation(
      @RequestHeader("Authorization") String authorization,
      @PathVariable Long id,
      @Valid @RequestBody LocationUpdateRequest request) {
    return orderService.updateLocation(authService.requireUser(authorization), id, request);
  }

  @PostMapping("/{id}/accept")
  public OrderResponse accept(
      @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
    return orderService.accept(authService.requireUser(authorization), id);
  }

  @PostMapping("/{id}/unable")
  public CourierReassignmentService.ReleaseResponse reportUnable(
      @RequestHeader("Authorization") String authorization,
      @PathVariable Long id,
      @Valid @RequestBody UnableRequest request) {
    return reassignmentService.reportUnable(
        authService.requireUser(authorization), id, request.reason());
  }

  @PostMapping("/{id}/cancel")
  public OrderResponse cancel(
      @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
    return orderService.cancel(authService.requireUser(authorization), id);
  }

  @PostMapping(value = "/{id}/proofs/{type}", consumes = "multipart/form-data")
  public OrderProofService.ProofResponse uploadProof(
      @RequestHeader("Authorization") String authorization,
      @PathVariable Long id,
      @PathVariable OrderProofType type,
      @RequestPart("file") MultipartFile file) {
    return proofService.upload(authService.requireUser(authorization), id, type, file);
  }

  @PostMapping("/{id}/package-size")
  public OrderResponse confirmPackageSize(
      @RequestHeader("Authorization") String authorization,
      @PathVariable Long id,
      @Valid @RequestBody ConfirmPackageSizeRequest request) {
    return orderService.confirmPackageSize(authService.requireUser(authorization), id, request);
  }

  @PostMapping("/{id}/package-size/approve")
  public OrderResponse approvePackageSize(
      @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
    return orderService.approvePackageSize(authService.requireUser(authorization), id);
  }

  @PostMapping("/{id}/package-size/reject")
  public OrderResponse rejectPackageSize(
      @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
    return orderService.rejectPackageSize(authService.requireUser(authorization), id);
  }

  @PostMapping("/{id}/pickup")
  public OrderResponse pickup(
      @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
    return orderService.markPickedUp(authService.requireUser(authorization), id);
  }

  @PostMapping("/{id}/manual-payment/request")
  public ManualQrPaymentService.ManualPaymentResponse requestManualPayment(
      @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
    return manualQrPaymentService.requestPayment(authService.requireUser(authorization), id);
  }

  @PostMapping("/{id}/manual-payment/submit")
  public ManualQrPaymentService.ManualPaymentResponse submitManualPayment(
      @RequestHeader("Authorization") String authorization,
      @PathVariable Long id,
      @Valid @RequestBody SubmitManualPaymentRequest request) {
    return manualQrPaymentService.submitPayment(
        authService.requireUser(authorization), id, request.payerReference());
  }

  @GetMapping("/{id}/manual-payment")
  public ManualQrPaymentService.ManualPaymentResponse manualPaymentStatus(
      @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
    return manualQrPaymentService.status(authService.requireUser(authorization), id);
  }

  @GetMapping("/{id}/delivery-code")
  public DeliveryCodeResponse deliveryCode(
      @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
    return orderService.deliveryCode(authService.requireUser(authorization), id);
  }

  @PostMapping("/{id}/complete")
  public OrderResponse complete(
      @RequestHeader("Authorization") String authorization,
      @PathVariable Long id,
      @Valid @RequestBody CompleteOrderRequest request) {
    return orderService.complete(authService.requireUser(authorization), id, request);
  }

  @PostMapping("/{id}/rating")
  public OrderResponse rate(
      @RequestHeader("Authorization") String authorization,
      @PathVariable Long id,
      @Valid @RequestBody CreateRatingRequest request) {
    return orderService.rate(authService.requireUser(authorization), id, request);
  }

  @GetMapping("/ratings/me")
  public CourierRatingResponse courierRating(@RequestHeader("Authorization") String authorization) {
    return orderService.courierRating(authService.requireUser(authorization));
  }

  @GetMapping("/{id}/issues")
  public List<OrderIssueService.IssueResponse> issues(
      @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
    return issueService.list(authService.requireUser(authorization), id);
  }

  @GetMapping("/{id}/chat")
  public List<OrderChatService.ChatResponse> chat(
      @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
    return chatService.list(authService.requireUser(authorization), id);
  }

  @GetMapping("/chat/unread")
  public OrderChatService.UnreadSummaryResponse unreadChat(
      @RequestHeader("Authorization") String authorization) {
    return chatService.unread(authService.requireUser(authorization));
  }

  @PostMapping("/{id}/chat")
  public OrderChatService.ChatResponse sendChat(
      @RequestHeader("Authorization") String authorization,
      @PathVariable Long id,
      @Valid @RequestBody ChatRequest request) {
    return chatService.send(authService.requireUser(authorization), id, request.content());
  }

  @PostMapping("/{id}/issues")
  public OrderIssueService.IssueResponse createIssue(
      @RequestHeader("Authorization") String authorization,
      @PathVariable Long id,
      @Valid @RequestBody CreateIssueRequest request) {
    return issueService.create(
        authService.requireUser(authorization), id, request.type(), request.description());
  }

  public record CreateOrderRequest(
      @NotBlank(message = "请输入商家名称") @Size(max = 80) String merchant,
      @NotBlank(message = "请输入取餐码") @Size(max = 32) String pickupCode,
      @NotBlank(message = "请输入取餐点") @Size(max = 120) String pickupLocation,
      @NotBlank(message = "请输入送达位置") @Size(max = 120) String destination,
      @NotNull(message = "请选择服务方式") ServiceType serviceType,
      Long couponId,
      boolean useMembership,
      PickupStation pickupStation,
      DeliveryZone deliveryZone) {
    public CreateOrderRequest(
        String merchant,
        String pickupCode,
        String pickupLocation,
        String destination,
        ServiceType serviceType,
        Long couponId,
        boolean useMembership) {
      this(
          merchant,
          pickupCode,
          pickupLocation,
          destination,
          serviceType,
          couponId,
          useMembership,
          PickupStation.fromLegacy(serviceType),
          DeliveryZone.fromLegacy(destination));
    }
  }

  public record OrderResponse(
      Long id,
      String orderNo,
      String merchant,
      String pickupCode,
      String pickupLocation,
      String destination,
      PickupStation pickupStation,
      DeliveryZone deliveryZone,
      ServiceType serviceType,
      OrderStatus status,
      int originalFeeCents,
      int discountCents,
      int feeCents,
      OrderPaymentMethod paymentMethod,
      OrderPaymentStatus paymentStatus,
      int paidCents,
      ManualPaymentStatus manualPaymentStatus,
      String payerReference,
      Instant paymentRequestedAt,
      Instant paymentSubmittedAt,
      Instant paymentReviewedAt,
      String paymentReviewNote,
      CourierSettlementStatus courierSettlementStatus,
      Instant courierSettledAt,
      String couponTitle,
      boolean membershipUsed,
      String membershipPlanTitle,
      String courierName,
      boolean packageSizeReviewProofUploaded,
      boolean pickupProofUploaded,
      boolean deliveryProofUploaded,
      boolean packageSizeConfirmed,
      ServiceType proposedPackageServiceType,
      Integer ratingScore,
      String ratingComment,
      Instant ratedAt,
      List<PaymentService.RefundResponse> refunds,
      Instant createdAt,
      Instant updatedAt) {}

  public record MineOrderPage(
      List<OrderResponse> items, int page, int size, long totalElements, int totalPages) {}

  public record OrderEventResponse(
      Long id, OrderStatus status, String actorName, Instant createdAt) {}

  public record LocationUpdateRequest(
      @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0") double latitude,
      @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0") double longitude) {}

  public record CourierLocationResponse(
      boolean available, Double latitude, Double longitude, Instant updatedAt) {}

  public record CreateIssueRequest(
      @NotNull(message = "请选择异常类型") OrderIssueType type,
      @NotBlank(message = "请输入异常说明") @Size(max = 500, message = "异常说明不能超过500字")
          String description) {}

  public record UnableRequest(
      @NotBlank(message = "请输入无法履约原因") @Size(max = 300, message = "原因不能超过300字") String reason) {}

  public record ChatRequest(
      @NotBlank(message = "请输入消息") @Size(max = 500, message = "消息不能超过500字") String content) {}

  public record CreateRatingRequest(
      @Min(value = 1, message = "评分不能低于1星") @Max(value = 5, message = "评分不能高于5星") int score,
      @Size(max = 300, message = "评价不能超过300字") String comment) {}

  public record CompleteOrderRequest(
      @NotBlank(message = "请输入收货码") @Pattern(regexp = "^[0-9]{6}$", message = "请输入 6 位数字收货码")
          String deliveryCode) {}

  public record SubmitManualPaymentRequest(
      @NotBlank(message = "请输入付款账号备注") @Size(max = 64, message = "付款账号备注不能超过64字")
          String payerReference) {}

  public record ConfirmPackageSizeRequest(@NotNull(message = "请选择包裹规格") ServiceType serviceType) {}

  public record DeliveryCodeResponse(String deliveryCode) {}

  public record CourierRatingResponse(double averageScore, long ratingCount) {}
}
