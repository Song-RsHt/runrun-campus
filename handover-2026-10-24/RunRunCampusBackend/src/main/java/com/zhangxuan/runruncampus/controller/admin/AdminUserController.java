package com.zhangxuan.runruncampus.controller.admin;

import com.zhangxuan.runruncampus.dto.AdminUserPasswordResetRequest;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.enums.admin.AdminPermission;
import com.zhangxuan.runruncampus.service.AuthService;
import com.zhangxuan.runruncampus.service.admin.AdminCouponService;
import com.zhangxuan.runruncampus.service.admin.AdminCouponService.GrantResponse;
import com.zhangxuan.runruncampus.service.admin.AdminUserService;
import com.zhangxuan.runruncampus.vo.AdminUserItem;
import com.zhangxuan.runruncampus.vo.AdminUserPageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
























@Validated
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

  private final AuthService authService;

  private final AdminUserService adminUserService;

  private final AdminCouponService adminCouponService;

  public AdminUserController(
      AuthService authService,
      AdminUserService adminUserService,
      AdminCouponService adminCouponService) {
    this.authService = authService;
    this.adminUserService = adminUserService;
    this.adminCouponService = adminCouponService;
  }

  @GetMapping
  public AdminUserPageResponse list(
      @RequestHeader("Authorization") String authorization,
      @RequestParam(required = false) String query,
      @RequestParam(required = false) UserRole role,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    authService.requirePermission(authorization, AdminPermission.USER_VIEW);
    return adminUserService.list(query, role, page, size);
  }

  @PostMapping("/{id}/suspend")
  public AdminUserItem suspend(
      @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
    return adminUserService.suspend(
        authService.requirePermission(authorization, AdminPermission.USER_MANAGE), id);
  }

  @PostMapping("/{id}/restore")
  public AdminUserItem restore(
      @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
    return adminUserService.restore(
        authService.requirePermission(authorization, AdminPermission.USER_MANAGE), id);
  }

  @PostMapping("/{id}/unlock-login")
  public AdminUserItem unlockLogin(
      @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
    return adminUserService.unlockLogin(
        authService.requirePermission(authorization, AdminPermission.USER_MANAGE), id);
  }

  @PutMapping("/{id}/password")
  public AdminUserItem resetPassword(
      @RequestHeader("Authorization") String authorization,
      @PathVariable Long id,
      @Valid @RequestBody AdminUserPasswordResetRequest request) {
    return adminUserService.resetPassword(
        authService.requirePermission(authorization, AdminPermission.USER_MANAGE),
        id,
        request.password());
  }

  @PostMapping("/{id}/coupons/one-yuan")
  public GrantResponse issueOneYuanCoupon(
      @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
    return adminCouponService.issueOneYuan(
        authService.requirePermission(authorization, AdminPermission.COUPON_ISSUE), id);
  }
}
