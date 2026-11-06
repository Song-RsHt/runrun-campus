package com.zhangxuan.runruncampus.controller;

import com.zhangxuan.runruncampus.dto.ChatRequest;
import com.zhangxuan.runruncampus.dto.CompleteOrderRequest;
import com.zhangxuan.runruncampus.dto.ConfirmPackageSizeRequest;
import com.zhangxuan.runruncampus.dto.CreateIssueRequest;
import com.zhangxuan.runruncampus.dto.CreateOrderRequest;
import com.zhangxuan.runruncampus.dto.CreateRatingRequest;
import com.zhangxuan.runruncampus.dto.LocationUpdateRequest;
import com.zhangxuan.runruncampus.dto.SubmitManualPaymentRequest;
import com.zhangxuan.runruncampus.dto.UnableRequest;
import com.zhangxuan.runruncampus.enums.OrderProofType;
import com.zhangxuan.runruncampus.enums.OrderStatus;
import com.zhangxuan.runruncampus.service.AuthService;
import com.zhangxuan.runruncampus.service.CourierReassignmentService;
import com.zhangxuan.runruncampus.service.ManualQrPaymentService;
import com.zhangxuan.runruncampus.service.OrderChatService;
import com.zhangxuan.runruncampus.service.OrderIssueService;
import com.zhangxuan.runruncampus.service.OrderProofService;
import com.zhangxuan.runruncampus.service.OrderService;
import com.zhangxuan.runruncampus.vo.CourierLocationResponse;
import com.zhangxuan.runruncampus.vo.CourierRatingResponse;
import com.zhangxuan.runruncampus.vo.DeliveryCodeResponse;
import com.zhangxuan.runruncampus.vo.ManualPaymentResponse;
import com.zhangxuan.runruncampus.vo.MineOrderPage;
import com.zhangxuan.runruncampus.vo.OrderChatVO;
import com.zhangxuan.runruncampus.vo.OrderEventResponse;
import com.zhangxuan.runruncampus.vo.OrderIssueVO;
import com.zhangxuan.runruncampus.vo.OrderProofVO;
import com.zhangxuan.runruncampus.vo.OrderResponse;
import com.zhangxuan.runruncampus.vo.TaskReleaseVO;
import com.zhangxuan.runruncampus.vo.UnreadChatSummaryVO;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
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

    public OrderController(AuthService authService, OrderService orderService, OrderProofService proofService, OrderIssueService issueService, CourierReassignmentService reassignmentService, OrderChatService chatService, ManualQrPaymentService manualQrPaymentService) {
        this.authService = authService;
        this.orderService = orderService;
        this.proofService = proofService;
        this.issueService = issueService;
        this.reassignmentService = reassignmentService;
        this.chatService = chatService;
        this.manualQrPaymentService = manualQrPaymentService;
    }

    @PostMapping
    public OrderResponse create(@RequestHeader("Authorization") String authorization, @RequestHeader(value = "Idempotency-Key", required = false) UUID requestKey, @Valid @RequestBody CreateOrderRequest request) {
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
    public MineOrderPage mineHistory(@RequestHeader("Authorization") String authorization, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) OrderStatus status, @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate startDate, @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate endDate) {
        return orderService.mineHistory(authService.requireUser(authorization), page, size, status, startDate, endDate);
    }

    @GetMapping("/available")
    public List<OrderResponse> available(@RequestHeader("Authorization") String authorization) {
        return orderService.available(authService.requireUser(authorization));
    }

    @GetMapping("/{id}")
    public OrderResponse get(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        return orderService.get(authService.requireUser(authorization), id);
    }

    @GetMapping("/{id}/events")
    public List<OrderEventResponse> events(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        return orderService.events(authService.requireUser(authorization), id);
    }

    @GetMapping("/{id}/location")
    public CourierLocationResponse location(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        return orderService.location(authService.requireUser(authorization), id);
    }

    @PostMapping("/{id}/location")
    public CourierLocationResponse updateLocation(@RequestHeader("Authorization") String authorization, @PathVariable Long id, @Valid @RequestBody LocationUpdateRequest request) {
        return orderService.updateLocation(authService.requireUser(authorization), id, request);
    }

    @PostMapping("/{id}/accept")
    public OrderResponse accept(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        return orderService.accept(authService.requireUser(authorization), id);
    }

    @PostMapping("/{id}/unable")
    public TaskReleaseVO reportUnable(@RequestHeader("Authorization") String authorization, @PathVariable Long id, @Valid @RequestBody UnableRequest request) {
        return reassignmentService.reportUnable(authService.requireUser(authorization), id, request.reason());
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        return orderService.cancel(authService.requireUser(authorization), id);
    }

    @PostMapping(value = "/{id}/proofs/{type}", consumes = "multipart/form-data")
    public OrderProofVO uploadProof(@RequestHeader("Authorization") String authorization, @PathVariable Long id, @PathVariable OrderProofType type, @RequestPart("file") MultipartFile file) {
        return proofService.upload(authService.requireUser(authorization), id, type, com.zhangxuan.runruncampus.integration.web.MultipartUploads.adapt(file));
    }

    @PostMapping("/{id}/package-size")
    public OrderResponse confirmPackageSize(@RequestHeader("Authorization") String authorization, @PathVariable Long id, @Valid @RequestBody ConfirmPackageSizeRequest request) {
        return orderService.confirmPackageSize(authService.requireUser(authorization), id, request);
    }

    @PostMapping("/{id}/package-size/approve")
    public OrderResponse approvePackageSize(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        return orderService.approvePackageSize(authService.requireUser(authorization), id);
    }

    @PostMapping("/{id}/package-size/reject")
    public OrderResponse rejectPackageSize(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        return orderService.rejectPackageSize(authService.requireUser(authorization), id);
    }

    @PostMapping("/{id}/pickup")
    public OrderResponse pickup(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        return orderService.markPickedUp(authService.requireUser(authorization), id);
    }

    @PostMapping("/{id}/manual-payment/request")
    public ManualPaymentResponse requestManualPayment(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        return manualQrPaymentService.requestPayment(authService.requireUser(authorization), id);
    }

    @PostMapping("/{id}/manual-payment/submit")
    public ManualPaymentResponse submitManualPayment(@RequestHeader("Authorization") String authorization, @PathVariable Long id, @Valid @RequestBody SubmitManualPaymentRequest request) {
        return manualQrPaymentService.submitPayment(authService.requireUser(authorization), id, request.payerReference());
    }

    @GetMapping("/{id}/manual-payment")
    public ManualPaymentResponse manualPaymentStatus(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        return manualQrPaymentService.status(authService.requireUser(authorization), id);
    }

    @GetMapping("/{id}/delivery-code")
    public DeliveryCodeResponse deliveryCode(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        return orderService.deliveryCode(authService.requireUser(authorization), id);
    }

    @PostMapping("/{id}/complete")
    public OrderResponse complete(@RequestHeader("Authorization") String authorization, @PathVariable Long id, @Valid @RequestBody CompleteOrderRequest request) {
        return orderService.complete(authService.requireUser(authorization), id, request);
    }

    @PostMapping("/{id}/rating")
    public OrderResponse rate(@RequestHeader("Authorization") String authorization, @PathVariable Long id, @Valid @RequestBody CreateRatingRequest request) {
        return orderService.rate(authService.requireUser(authorization), id, request);
    }

    @GetMapping("/ratings/me")
    public CourierRatingResponse courierRating(@RequestHeader("Authorization") String authorization) {
        return orderService.courierRating(authService.requireUser(authorization));
    }

    @GetMapping("/{id}/issues")
    public List<OrderIssueVO> issues(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        return issueService.list(authService.requireUser(authorization), id);
    }

    @GetMapping("/{id}/chat")
    public List<OrderChatVO> chat(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        return chatService.list(authService.requireUser(authorization), id);
    }

    @GetMapping("/chat/unread")
    public UnreadChatSummaryVO unreadChat(@RequestHeader("Authorization") String authorization) {
        return chatService.unread(authService.requireUser(authorization));
    }

    @PostMapping("/{id}/chat")
    public OrderChatVO sendChat(@RequestHeader("Authorization") String authorization, @PathVariable Long id, @Valid @RequestBody ChatRequest request) {
        return chatService.send(authService.requireUser(authorization), id, request.content());
    }

    @PostMapping("/{id}/issues")
    public OrderIssueVO createIssue(@RequestHeader("Authorization") String authorization, @PathVariable Long id, @Valid @RequestBody CreateIssueRequest request) {
        return issueService.create(authService.requireUser(authorization), id, request.type(), request.description());
    }
}
