package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.controller.AdminAccountController;
import com.zhangxuan.runruncampus.controller.AdminAuditController;
import com.zhangxuan.runruncampus.controller.AdminCommandCenterController;
import com.zhangxuan.runruncampus.controller.AdminDispatchController;
import com.zhangxuan.runruncampus.controller.AdminIssueController;
import com.zhangxuan.runruncampus.controller.AdminOrderController;
import com.zhangxuan.runruncampus.controller.AdminOrderLifecycleController;
import com.zhangxuan.runruncampus.controller.AdminUserController;
import com.zhangxuan.runruncampus.dto.AdminUserPasswordResetRequest;
import com.zhangxuan.runruncampus.dto.CreateAdminRequest;
import com.zhangxuan.runruncampus.dto.DispatchRequest;
import com.zhangxuan.runruncampus.dto.OrderLifecycleReasonRequest;
import com.zhangxuan.runruncampus.enums.AdminPermission;
import com.zhangxuan.runruncampus.enums.AdminRole;
import com.zhangxuan.runruncampus.enums.Queue;
import com.zhangxuan.runruncampus.service.AdminAccountService;
import com.zhangxuan.runruncampus.service.AdminAuditService;
import com.zhangxuan.runruncampus.service.AdminCommandCenterService;
import com.zhangxuan.runruncampus.service.AdminCouponService;
import com.zhangxuan.runruncampus.service.AdminDispatchService;
import com.zhangxuan.runruncampus.service.AdminIssueService;
import com.zhangxuan.runruncampus.service.AdminOrderLifecycleService;
import com.zhangxuan.runruncampus.service.AdminOrderService;
import com.zhangxuan.runruncampus.service.AdminUserService;
import com.zhangxuan.runruncampus.service.AuthService;
import com.zhangxuan.runruncampus.service.ManualQrPaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;




















































































































@ExtendWith(MockitoExtension.class)
class AdminAuthorizationControllerTest {

  private static final String TOKEN = "Bearer restricted-admin";

  @Mock private AuthService authService;

  @Mock private AdminOrderService orderService;

  @Mock private ManualQrPaymentService manualQrPaymentService;

  @Mock private AdminIssueService issueService;

  @Mock private AdminUserService userService;

  @Mock private AdminCouponService couponService;

  @Mock private AdminAccountService accountService;

  @Mock private AdminAuditService auditService;

  @Mock private AdminCommandCenterService commandCenterService;

  @Mock private AdminDispatchService dispatchService;

  @Mock private AdminOrderLifecycleService lifecycleService;

  @Test
  void commandCenterRequiresDashboardPermissionBeforeReadingData() {
    deny(AdminPermission.DASHBOARD_VIEW);
    var controller = new AdminCommandCenterController(authService, commandCenterService);
    var exception =
        assertThrows(
            ResponseStatusException.class, () -> controller.snapshot(TOKEN, Queue.WAITING, 0, 0));
    assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    verifyNoInteractions(commandCenterService);
  }

  @Test
  void administratorWithoutExportPermissionCannotExportOrders() {
    deny(AdminPermission.ORDER_EXPORT);
    AdminOrderController controller =
        new AdminOrderController(authService, orderService, manualQrPaymentService);
    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class, () -> controller.export(TOKEN, null, null, false));
    assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    verifyNoInteractions(orderService);
  }

  @Test
  void administratorWithoutPaymentReviewPermissionCannotConfirmManualPayment() {
    deny(AdminPermission.PAYMENT_REVIEW);
    AdminOrderController controller =
        new AdminOrderController(authService, orderService, manualQrPaymentService);
    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class, () -> controller.confirmManualPayment(TOKEN, 11L));
    assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    verifyNoInteractions(manualQrPaymentService);
  }

  @Test
  void administratorWithoutOverridePermissionCannotForceCompleteOrder() {
    deny(AdminPermission.ORDER_FORCE_COMPLETE);
    var controller = new AdminOrderLifecycleController(authService, lifecycleService);
    var error =
        assertThrows(
            ResponseStatusException.class,
            () -> controller.forceComplete(TOKEN, 11L, new OrderLifecycleReasonRequest("凭证已核对")));
    assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    verifyNoInteractions(lifecycleService);
  }

  @Test
  void administratorWithoutArchivePermissionCannotArchiveOrder() {
    deny(AdminPermission.ORDER_ARCHIVE);
    var controller = new AdminOrderLifecycleController(authService, lifecycleService);
    var error =
        assertThrows(
            ResponseStatusException.class,
            () -> controller.archive(TOKEN, 11L, new OrderLifecycleReasonRequest("订单归档原因")));
    assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    verifyNoInteractions(lifecycleService);
  }

  @Test
  void readOnlyAdministratorCannotDispatchOrder() {
    deny(AdminPermission.ORDER_DISPATCH);
    AdminDispatchController controller = new AdminDispatchController(authService, dispatchService);
    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () -> controller.dispatch(TOKEN, 11L, new DispatchRequest(3L)));
    assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    verifyNoInteractions(dispatchService);
  }

  @Test
  void readOnlyAdministratorCannotClaimIssue() {
    deny(AdminPermission.ISSUE_MANAGE);
    AdminIssueController controller = new AdminIssueController(authService, issueService);
    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> controller.claim(TOKEN, 11L));
    assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    verifyNoInteractions(issueService);
  }

  @Test
  void administratorWithoutUserManagePermissionCannotSuspendUser() {
    deny(AdminPermission.USER_MANAGE);
    AdminUserController controller =
        new AdminUserController(authService, userService, couponService);
    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> controller.suspend(TOKEN, 11L));
    assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    verifyNoInteractions(userService);
  }

  @Test
  void administratorWithoutUserManagePermissionCannotResetUserPassword() {
    deny(AdminPermission.USER_MANAGE);
    AdminUserController controller =
        new AdminUserController(authService, userService, couponService);
    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () -> controller.resetPassword(TOKEN, 11L, new AdminUserPasswordResetRequest("new-password")));
    assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    verifyNoInteractions(userService);
  }

  @Test
  void administratorWithoutCouponPermissionCannotIssueCoupon() {
    deny(AdminPermission.COUPON_ISSUE);
    AdminUserController controller =
        new AdminUserController(authService, userService, couponService);
    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class, () -> controller.issueOneYuanCoupon(TOKEN, 11L));
    assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    verifyNoInteractions(couponService);
  }

  @Test
  void administratorWithoutAdminManagePermissionCannotCreateAdministrator() {
    deny(AdminPermission.ADMIN_MANAGE);
    AdminAccountController controller = new AdminAccountController(authService, accountService);
    CreateAdminRequest request =
        new CreateAdminRequest("13800000001", "password", "新管理员", AdminRole.SUPPORT);
    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> controller.create(TOKEN, request));
    assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    verifyNoInteractions(accountService);
  }

  @Test
  void administratorWithoutAuditPermissionCannotReadAuditLog() {
    deny(AdminPermission.AUDIT_VIEW);
    AdminAuditController controller = new AdminAuditController(authService, auditService);
    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class, () -> controller.list(TOKEN, null, null, 0, 20));
    assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    verifyNoInteractions(auditService);
  }

  private void deny(AdminPermission permission) {
    when(authService.requirePermission(TOKEN, permission))
        .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "当前管理员岗位无此权限"));
  }
}
