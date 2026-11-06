package com.zhangxuan.runruncampus.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.zhangxuan.runruncampus.dto.AdminUserPasswordResetRequest;
import com.zhangxuan.runruncampus.dto.CreateAdminRequest;
import com.zhangxuan.runruncampus.dto.DispatchRequest;
import com.zhangxuan.runruncampus.dto.OrderLifecycleReasonRequest;
import com.zhangxuan.runruncampus.enums.AdminPermission;
import com.zhangxuan.runruncampus.enums.AdminRole;
import com.zhangxuan.runruncampus.enums.Queue;
import com.zhangxuan.runruncampus.exception.BusinessFailure;
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
                        BusinessFailure.class,
                        () -> controller.snapshot(TOKEN, Queue.WAITING, 0, 0));
        assertEquals(HttpStatus.FORBIDDEN.value(), exception.status());
        verifyNoInteractions(commandCenterService);
    }

    @Test
    void administratorWithoutExportPermissionCannotExportOrders() {
        deny(AdminPermission.ORDER_EXPORT);
        AdminOrderController controller =
                new AdminOrderController(authService, orderService, manualQrPaymentService);
        BusinessFailure exception =
                assertThrows(
                        BusinessFailure.class, () -> controller.export(TOKEN, null, null, false));
        assertEquals(HttpStatus.FORBIDDEN.value(), exception.status());
        verifyNoInteractions(orderService);
    }

    @Test
    void administratorWithoutPaymentReviewPermissionCannotConfirmManualPayment() {
        deny(AdminPermission.PAYMENT_REVIEW);
        AdminOrderController controller =
                new AdminOrderController(authService, orderService, manualQrPaymentService);
        BusinessFailure exception =
                assertThrows(
                        BusinessFailure.class, () -> controller.confirmManualPayment(TOKEN, 11L));
        assertEquals(HttpStatus.FORBIDDEN.value(), exception.status());
        verifyNoInteractions(manualQrPaymentService);
    }

    @Test
    void administratorWithoutOverridePermissionCannotForceCompleteOrder() {
        deny(AdminPermission.ORDER_FORCE_COMPLETE);
        var controller = new AdminOrderLifecycleController(authService, lifecycleService);
        var error =
                assertThrows(
                        BusinessFailure.class,
                        () ->
                                controller.forceComplete(
                                        TOKEN, 11L, new OrderLifecycleReasonRequest("凭证已核对")));
        assertEquals(HttpStatus.FORBIDDEN.value(), error.status());
        verifyNoInteractions(lifecycleService);
    }

    @Test
    void administratorWithoutArchivePermissionCannotArchiveOrder() {
        deny(AdminPermission.ORDER_ARCHIVE);
        var controller = new AdminOrderLifecycleController(authService, lifecycleService);
        var error =
                assertThrows(
                        BusinessFailure.class,
                        () ->
                                controller.archive(
                                        TOKEN, 11L, new OrderLifecycleReasonRequest("订单归档原因")));
        assertEquals(HttpStatus.FORBIDDEN.value(), error.status());
        verifyNoInteractions(lifecycleService);
    }

    @Test
    void readOnlyAdministratorCannotDispatchOrder() {
        deny(AdminPermission.ORDER_DISPATCH);
        AdminDispatchController controller =
                new AdminDispatchController(authService, dispatchService);
        BusinessFailure exception =
                assertThrows(
                        BusinessFailure.class,
                        () -> controller.dispatch(TOKEN, 11L, new DispatchRequest(3L)));
        assertEquals(HttpStatus.FORBIDDEN.value(), exception.status());
        verifyNoInteractions(dispatchService);
    }

    @Test
    void readOnlyAdministratorCannotClaimIssue() {
        deny(AdminPermission.ISSUE_MANAGE);
        AdminIssueController controller = new AdminIssueController(authService, issueService);
        BusinessFailure exception =
                assertThrows(BusinessFailure.class, () -> controller.claim(TOKEN, 11L));
        assertEquals(HttpStatus.FORBIDDEN.value(), exception.status());
        verifyNoInteractions(issueService);
    }

    @Test
    void administratorWithoutUserManagePermissionCannotSuspendUser() {
        deny(AdminPermission.USER_MANAGE);
        AdminUserController controller =
                new AdminUserController(authService, userService, couponService);
        BusinessFailure exception =
                assertThrows(BusinessFailure.class, () -> controller.suspend(TOKEN, 11L));
        assertEquals(HttpStatus.FORBIDDEN.value(), exception.status());
        verifyNoInteractions(userService);
    }

    @Test
    void administratorWithoutUserManagePermissionCannotResetUserPassword() {
        deny(AdminPermission.USER_MANAGE);
        AdminUserController controller =
                new AdminUserController(authService, userService, couponService);
        BusinessFailure exception =
                assertThrows(
                        BusinessFailure.class,
                        () ->
                                controller.resetPassword(
                                        TOKEN,
                                        11L,
                                        new AdminUserPasswordResetRequest("new-password")));
        assertEquals(HttpStatus.FORBIDDEN.value(), exception.status());
        verifyNoInteractions(userService);
    }

    @Test
    void administratorWithoutCouponPermissionCannotIssueCoupon() {
        deny(AdminPermission.COUPON_ISSUE);
        AdminUserController controller =
                new AdminUserController(authService, userService, couponService);
        BusinessFailure exception =
                assertThrows(
                        BusinessFailure.class, () -> controller.issueOneYuanCoupon(TOKEN, 11L));
        assertEquals(HttpStatus.FORBIDDEN.value(), exception.status());
        verifyNoInteractions(couponService);
    }

    @Test
    void administratorWithoutAdminManagePermissionCannotCreateAdministrator() {
        deny(AdminPermission.ADMIN_MANAGE);
        AdminAccountController controller = new AdminAccountController(authService, accountService);
        CreateAdminRequest request =
                new CreateAdminRequest("13800000001", "password", "新管理员", AdminRole.SUPPORT);
        BusinessFailure exception =
                assertThrows(BusinessFailure.class, () -> controller.create(TOKEN, request));
        assertEquals(HttpStatus.FORBIDDEN.value(), exception.status());
        verifyNoInteractions(accountService);
    }

    @Test
    void administratorWithoutAuditPermissionCannotReadAuditLog() {
        deny(AdminPermission.AUDIT_VIEW);
        AdminAuditController controller = new AdminAuditController(authService, auditService);
        BusinessFailure exception =
                assertThrows(
                        BusinessFailure.class, () -> controller.list(TOKEN, null, null, 0, 20));
        assertEquals(HttpStatus.FORBIDDEN.value(), exception.status());
        verifyNoInteractions(auditService);
    }

    private void deny(AdminPermission permission) {
        when(authService.requirePermission(TOKEN, permission))
                .thenThrow(new BusinessFailure(HttpStatus.FORBIDDEN.value(), "当前管理员岗位无此权限"));
    }
}
