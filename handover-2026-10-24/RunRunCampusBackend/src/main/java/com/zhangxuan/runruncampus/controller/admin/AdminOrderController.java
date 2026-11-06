package com.zhangxuan.runruncampus.controller.admin;

import com.zhangxuan.runruncampus.dto.request.admin.order.RejectManualPaymentRequest;
import com.zhangxuan.runruncampus.dto.response.admin.order.AdminOrderDetailResponse;
import com.zhangxuan.runruncampus.dto.response.admin.order.AdminOrderPageResponse;
import com.zhangxuan.runruncampus.enums.admin.AdminPermission;
import com.zhangxuan.runruncampus.enums.order.OrderStatus;
import com.zhangxuan.runruncampus.service.AuthService;
import com.zhangxuan.runruncampus.service.admin.AdminOrderService;
import com.zhangxuan.runruncampus.service.admin.AdminOrderService.OrderExport;
import com.zhangxuan.runruncampus.service.payment.ManualQrPaymentService;
import com.zhangxuan.runruncampus.service.payment.ManualQrPaymentService.ManualPaymentResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


























@Validated
@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {

  private final AuthService authService;

  private final AdminOrderService adminOrderService;

  private final ManualQrPaymentService manualQrPaymentService;

  public AdminOrderController(
      AuthService authService,
      AdminOrderService adminOrderService,
      ManualQrPaymentService manualQrPaymentService) {
    this.authService = authService;
    this.adminOrderService = adminOrderService;
    this.manualQrPaymentService = manualQrPaymentService;
  }

  @GetMapping
  public AdminOrderPageResponse list(
      @RequestHeader("Authorization") String authorization,
      @RequestParam(required = false) String query,
      @RequestParam(required = false) OrderStatus status,
      @RequestParam(defaultValue = "false") boolean archived,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    authService.requirePermission(authorization, AdminPermission.ORDER_VIEW);
    return adminOrderService.list(query, status, archived, page, size);
  }

  @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
  public ResponseEntity<byte[]> export(
      @RequestHeader("Authorization") String authorization,
      @RequestParam(required = false) String query,
      @RequestParam(required = false) OrderStatus status,
      @RequestParam(defaultValue = "false") boolean archived) {
    authService.requirePermission(authorization, AdminPermission.ORDER_EXPORT);
    OrderExport export = adminOrderService.exportCsv(query, status, archived);
    return ResponseEntity.ok()
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=orders.csv")
        .header("X-Export-Limit", String.valueOf(AdminOrderService.EXPORT_LIMIT))
        .header("X-Export-Truncated", String.valueOf(export.truncated()))
        .body(export.content());
  }

  @GetMapping("/{id}")
  public AdminOrderDetailResponse detail(
      @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
    authService.requirePermission(authorization, AdminPermission.ORDER_VIEW);
    return adminOrderService.detail(id);
  }

  @PostMapping("/{id}/manual-payment/confirm")
  public ManualPaymentResponse confirmManualPayment(
      @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
    return manualQrPaymentService.confirmPayment(
        authService.requirePermission(authorization, AdminPermission.PAYMENT_REVIEW), id);
  }

  @PostMapping("/{id}/manual-payment/reject")
  public ManualPaymentResponse rejectManualPayment(
      @RequestHeader("Authorization") String authorization,
      @PathVariable Long id,
      @Valid @RequestBody RejectManualPaymentRequest request) {
    return manualQrPaymentService.rejectPayment(
        authService.requirePermission(authorization, AdminPermission.PAYMENT_REVIEW),
        id,
        request.reason());
  }

  @PostMapping("/{id}/courier-settlement/confirm")
  public ManualPaymentResponse confirmCourierSettlement(
      @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
    return manualQrPaymentService.confirmCourierSettlement(
        authService.requirePermission(authorization, AdminPermission.PAYMENT_REVIEW), id);
  }
}
