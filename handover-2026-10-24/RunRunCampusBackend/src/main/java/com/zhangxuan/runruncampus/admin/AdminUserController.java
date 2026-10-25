package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.auth.AuthService;
import com.zhangxuan.runruncampus.user.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.time.Instant;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private final AuthService authService;
    private final AdminUserService adminUserService;
    private final AdminCouponService adminCouponService;

    public AdminUserController(AuthService authService, AdminUserService adminUserService,
                               AdminCouponService adminCouponService) {
        this.authService = authService;
        this.adminUserService = adminUserService;
        this.adminCouponService = adminCouponService;
    }

    @GetMapping
    AdminUserPageResponse list(@RequestHeader("Authorization") String authorization,
                               @RequestParam(required = false) String query,
                               @RequestParam(required = false) UserRole role,
                               @RequestParam(defaultValue = "0") @Min(0) int page,
                               @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        authService.requirePermission(authorization, AdminPermission.USER_VIEW);
        return adminUserService.list(query, role, page, size);
    }

    @PostMapping("/{id}/suspend")
    AdminUserItem suspend(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        return adminUserService.suspend(authService.requirePermission(authorization, AdminPermission.USER_MANAGE), id);
    }

    @PostMapping("/{id}/restore")
    AdminUserItem restore(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        return adminUserService.restore(authService.requirePermission(authorization, AdminPermission.USER_MANAGE), id);
    }

    @PostMapping("/{id}/unlock-login")
    AdminUserItem unlockLogin(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        return adminUserService.unlockLogin(
                authService.requirePermission(authorization, AdminPermission.USER_MANAGE), id);
    }

    @PutMapping("/{id}/password")
    AdminUserItem resetPassword(@RequestHeader("Authorization") String authorization, @PathVariable Long id,
                                @Valid @RequestBody ResetPasswordRequest request) {
        return adminUserService.resetPassword(
                authService.requirePermission(authorization, AdminPermission.USER_MANAGE), id,
                request.password());
    }

    @PostMapping("/{id}/coupons/one-yuan")
    AdminCouponService.GrantResponse issueOneYuanCoupon(
            @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        return adminCouponService.issueOneYuan(
                authService.requirePermission(authorization, AdminPermission.COUPON_ISSUE), id);
    }

    public record AdminUserPageResponse(
            List<AdminUserItem> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) { }

    public record AdminUserItem(
            Long id,
            String displayName,
            String phone,
            UserRole role,
            boolean enabled,
            boolean courierApproved,
            Instant registeredAt,
            int failedLoginAttempts,
            Instant loginLockedUntil
    ) { }

    public record ResetPasswordRequest(
            @NotBlank(message = "请输入新密码")
            @Size(min = 6, max = 72, message = "密码长度应为 6 到 72 位") String password) { }
}
