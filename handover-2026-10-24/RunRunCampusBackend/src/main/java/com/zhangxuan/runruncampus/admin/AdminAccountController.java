package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.auth.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;

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
    PasswordChangedResponse changeOwnPassword(@RequestHeader("Authorization") String authorization,
                                              @Valid @RequestBody ChangeOwnPasswordRequest request) {
        accountService.changeOwnPassword(
                authService.requireAdmin(authorization), request.currentPassword(), request.newPassword());
        return new PasswordChangedResponse(true);
    }

    @GetMapping
    List<AdminAccountItem> list(@RequestHeader("Authorization") String authorization) {
        authService.requirePermission(authorization, AdminPermission.ADMIN_MANAGE);
        return accountService.list();
    }

    @PostMapping
    AdminAccountItem create(@RequestHeader("Authorization") String authorization,
                            @Valid @RequestBody CreateAdminRequest request) {
        return accountService.create(
                authService.requirePermission(authorization, AdminPermission.ADMIN_MANAGE),
                request.phone(), request.password(), request.displayName(), request.adminRole());
    }

    @PutMapping("/{id}/role")
    AdminAccountItem changeRole(@RequestHeader("Authorization") String authorization, @PathVariable Long id,
                                @Valid @RequestBody ChangeRoleRequest request) {
        return accountService.changeRole(
                authService.requirePermission(authorization, AdminPermission.ADMIN_MANAGE), id,
                request.adminRole());
    }

    @PostMapping("/{id}/suspend")
    AdminAccountItem suspend(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        return accountService.suspend(
                authService.requirePermission(authorization, AdminPermission.ADMIN_MANAGE), id);
    }

    @PostMapping("/{id}/restore")
    AdminAccountItem restore(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        return accountService.restore(
                authService.requirePermission(authorization, AdminPermission.ADMIN_MANAGE), id);
    }

    @PutMapping("/{id}/password")
    AdminAccountItem resetPassword(@RequestHeader("Authorization") String authorization, @PathVariable Long id,
                                   @Valid @RequestBody ResetPasswordRequest request) {
        return accountService.resetPassword(
                authService.requirePermission(authorization, AdminPermission.ADMIN_MANAGE), id,
                request.password());
    }

    @PostMapping("/{id}/unlock-login")
    AdminAccountItem unlockLogin(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        return accountService.unlockLogin(
                authService.requirePermission(authorization, AdminPermission.ADMIN_MANAGE), id);
    }

    public record AdminAccountItem(Long id, String phone, String displayName, AdminRole adminRole,
                                   Set<AdminPermission> permissions, boolean enabled, Instant createdAt,
                                   int failedLoginAttempts, Instant loginLockedUntil) { }

    public record CreateAdminRequest(
            @Pattern(regexp = "^1\\d{10}$", message = "请输入 11 位手机号") String phone,
            @Size(min = 8, max = 72, message = "管理员密码长度应为 8 到 72 位") String password,
            @NotBlank(message = "请输入姓名") @Size(max = 48, message = "姓名过长") String displayName,
            @NotNull(message = "请选择管理员岗位") AdminRole adminRole) { }

    public record ChangeRoleRequest(@NotNull(message = "请选择管理员岗位") AdminRole adminRole) { }

    public record ResetPasswordRequest(
            @Size(min = 8, max = 72, message = "管理员密码长度应为 8 到 72 位") String password) { }

    public record ChangeOwnPasswordRequest(
            @NotBlank(message = "请输入当前密码") String currentPassword,
            @Size(min = 8, max = 72, message = "新密码长度应为 8 到 72 位") String newPassword) { }

    public record PasswordChangedResponse(boolean changed) { }
}
