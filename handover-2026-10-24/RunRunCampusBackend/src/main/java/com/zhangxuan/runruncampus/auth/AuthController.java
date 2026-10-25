package com.zhangxuan.runruncampus.auth;

import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRole;
import com.zhangxuan.runruncampus.admin.AdminPermission;
import com.zhangxuan.runruncampus.admin.AdminRole;
import com.zhangxuan.runruncampus.api.ApiRateLimitService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final ApiRateLimitService rateLimitService;
    private final LoginCaptchaService captchaService;

    public AuthController(AuthService authService, ApiRateLimitService rateLimitService, LoginCaptchaService captchaService) {
        this.authService = authService;
        this.rateLimitService = rateLimitService;
        this.captchaService = captchaService;
    }

    @PostMapping("/register")
    AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return response(authService.register(request.phone(), request.password(), request.displayName(), request.role(),
                AuthService.SessionMetadata.of(request.clientType(), request.deviceName()), request.privacyPolicyVersion()));
    }

    @PostMapping("/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request) {
        if (!rateLimitService.tryAcquire("auth-login-account", request.phone(), 10)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
        }
        return response(authService.login(request.phone(), request.password(), request.captchaToken(), request.captchaCode(),
                AuthService.SessionMetadata.of(request.clientType(), request.deviceName())));
    }

    @PostMapping("/captcha")
    LoginCaptchaService.Challenge captcha() {
        return captchaService.issue();
    }

    @GetMapping("/sessions")
    java.util.List<AuthService.SessionInfo> sessions(@RequestHeader("Authorization") String authorization) {
        return authService.sessions(authorization);
    }

    @GetMapping("/me")
    UserResponse me(@RequestHeader("Authorization") String authorization) {
        return userResponse(authService.requireUser(authorization));
    }

    @DeleteMapping("/sessions/{id}")
    RevokeResponse revoke(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        authService.revokeSession(authorization, id);
        return new RevokeResponse(1);
    }

    @DeleteMapping("/sessions/others")
    RevokeResponse revokeOthers(@RequestHeader("Authorization") String authorization) {
        return new RevokeResponse(authService.revokeOtherSessions(authorization));
    }

    @PostMapping("/logout")
    RevokeResponse logout(@RequestHeader("Authorization") String authorization) {
        authService.logout(authorization);
        return new RevokeResponse(1);
    }

    @PostMapping("/password")
    ResponseEntity<Void> changePassword(@RequestHeader("Authorization") String authorization,
                                        @Valid @RequestBody ChangePasswordRequest request) {
        AppUser user = authService.requireUser(authorization);
        if (!rateLimitService.tryAcquire("auth-change-password", user.getId().toString(), 5)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
        }
        authService.changeOwnPassword(authorization, request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    private AuthResponse response(Session session) {
        return new AuthResponse(session.getToken(), userResponse(session.getUser()));
    }

    private UserResponse userResponse(AppUser user) {
        return new UserResponse(
                user.getId(), user.getPhone(), user.getDisplayName(), user.getRole(), user.getAdminRole(),
                user.getAdminRole() == null ? java.util.Set.of() : user.getAdminRole().getPermissions(),
                user.getPrivacyPolicyVersion(), user.getPrivacyConsentAt());
    }

    public record RegisterRequest(
            @Pattern(regexp = "^1\\d{10}$", message = "请输入 11 位手机号") String phone,
            @Size(min = 6, max = 72, message = "密码长度应为 6 到 72 位") String password,
            @NotBlank(message = "请输入昵称") @Size(max = 48, message = "昵称过长") String displayName,
            UserRole role,
            @Size(max = 16) String clientType,
            @Size(max = 80) String deviceName,
            @NotBlank(message = "请先同意隐私政策") @Size(max = 32) String privacyPolicyVersion
    ) {
        public RegisterRequest(String phone, String password, String displayName, UserRole role) {
            this(phone, password, displayName, role, null, null, com.zhangxuan.runruncampus.privacy.PrivacyService.CURRENT_VERSION);
        }
        public RegisterRequest {
            if (role == null) role = UserRole.CUSTOMER;
        }
    }

    public record LoginRequest(
            @Pattern(regexp = "^1\\d{10}$", message = "请输入 11 位手机号") String phone,
            @NotBlank(message = "请输入密码") String password,
            @Size(max = 64) String captchaToken,
            @Size(max = 8) String captchaCode,
            @Size(max = 16) String clientType,
            @Size(max = 80) String deviceName
    ) {
        public LoginRequest(String phone, String password) {
            this(phone, password, null, null, null, null);
        }
    }

    public record AuthResponse(String token, UserResponse user) { }
    public record ChangePasswordRequest(
            @NotBlank(message = "请输入当前密码") @Size(max = 72, message = "当前密码过长") String currentPassword,
            @NotBlank(message = "请输入新密码") @Size(min = 6, max = 72, message = "密码长度应为 6 到 72 位")
            String newPassword) { }
    public record UserResponse(Long id, String phone, String displayName, UserRole role,
                               AdminRole adminRole, java.util.Set<AdminPermission> permissions,
                               String privacyPolicyVersion, java.time.Instant privacyConsentAt) { }
    public record RevokeResponse(long revoked) { }
}
