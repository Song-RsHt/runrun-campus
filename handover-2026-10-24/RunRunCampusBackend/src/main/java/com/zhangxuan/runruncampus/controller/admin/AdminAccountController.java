package com.zhangxuan.runruncampus.controller.admin;

import com.zhangxuan.runruncampus.dto.request.admin.account.ChangeOwnPasswordRequest;
import com.zhangxuan.runruncampus.dto.request.admin.account.ChangeRoleRequest;
import com.zhangxuan.runruncampus.dto.request.admin.account.CreateAdminRequest;
import com.zhangxuan.runruncampus.dto.request.admin.account.ResetPasswordRequest;
import com.zhangxuan.runruncampus.dto.response.admin.account.AdminAccountItem;
import com.zhangxuan.runruncampus.dto.response.admin.account.PasswordChangedResponse;
import com.zhangxuan.runruncampus.enums.admin.AdminPermission;
import com.zhangxuan.runruncampus.service.admin.AdminAccountService;
import com.zhangxuan.runruncampus.service.auth.AuthService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/accounts")
public class AdminAccountController {

  private final AuthService authService;

  private final AdminAccountService accountService;

  public AdminAccountController(AuthService authService, AdminAccountService accountService) {
    this.authService = authService;
    this.accountService = accountService;
  }

  @PutMapping("/me/password")
  public PasswordChangedResponse changeOwnPassword(
      @RequestHeader("Authorization") String authorization,
      @Valid @RequestBody ChangeOwnPasswordRequest request) {
    accountService.changeOwnPassword(
        authService.requireAdmin(authorization), request.currentPassword(), request.newPassword());
    return new PasswordChangedResponse(true);
  }

  @GetMapping
  public List<AdminAccountItem> list(@RequestHeader("Authorization") String authorization) {
    authService.requirePermission(authorization, AdminPermission.ADMIN_MANAGE);
    return accountService.list();
  }

  @PostMapping
  public AdminAccountItem create(
      @RequestHeader("Authorization") String authorization,
      @Valid @RequestBody CreateAdminRequest request) {
    return accountService.create(
        authService.requirePermission(authorization, AdminPermission.ADMIN_MANAGE),
        request.phone(),
        request.password(),
        request.displayName(),
        request.adminRole());
  }

  @PutMapping("/{id}/role")
  public AdminAccountItem changeRole(
      @RequestHeader("Authorization") String authorization,
      @PathVariable Long id,
      @Valid @RequestBody ChangeRoleRequest request) {
    return accountService.changeRole(
        authService.requirePermission(authorization, AdminPermission.ADMIN_MANAGE),
        id,
        request.adminRole());
  }

  @PostMapping("/{id}/suspend")
  public AdminAccountItem suspend(
      @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
    return accountService.suspend(
        authService.requirePermission(authorization, AdminPermission.ADMIN_MANAGE), id);
  }

  @PostMapping("/{id}/restore")
  public AdminAccountItem restore(
      @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
    return accountService.restore(
        authService.requirePermission(authorization, AdminPermission.ADMIN_MANAGE), id);
  }

  @PutMapping("/{id}/password")
  public AdminAccountItem resetPassword(
      @RequestHeader("Authorization") String authorization,
      @PathVariable Long id,
      @Valid @RequestBody ResetPasswordRequest request) {
    return accountService.resetPassword(
        authService.requirePermission(authorization, AdminPermission.ADMIN_MANAGE),
        id,
        request.password());
  }

  @PostMapping("/{id}/unlock-login")
  public AdminAccountItem unlockLogin(
      @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
    return accountService.unlockLogin(
        authService.requirePermission(authorization, AdminPermission.ADMIN_MANAGE), id);
  }
}
