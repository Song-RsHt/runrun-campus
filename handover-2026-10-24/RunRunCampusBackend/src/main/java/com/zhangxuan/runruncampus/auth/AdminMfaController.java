package com.zhangxuan.runruncampus.auth;

import com.zhangxuan.runruncampus.admin.AdminPermission;
import com.zhangxuan.runruncampus.api.ApiRateLimitService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class AdminMfaController {
    private final AdminMfaService mfa;
    private final AuthService auth;
    private final ApiRateLimitService limits;

    public AdminMfaController(AdminMfaService mfa, AuthService auth, ApiRateLimitService limits) {
        this.mfa = mfa; this.auth = auth; this.limits = limits;
    }

    @PostMapping("/auth/admin/login")
    AdminMfaService.Challenge login(@Valid @RequestBody LoginRequest request) {
        limit("admin-mfa-login-account", request.phone());
        return mfa.begin(request.phone(), request.password());
    }

    @PostMapping("/auth/admin/verify")
    AdminMfaService.Verification verify(@Valid @RequestBody VerifyRequest request) {
        limit("admin-mfa-challenge", request.challengeToken());
        return mfa.verify(request.challengeToken(), request.code().trim(),
                AuthService.SessionMetadata.of(request.clientType(), request.deviceName()));
    }

    @PostMapping("/admin/accounts/me/mfa/replace")
    AdminMfaService.Challenge replace(@RequestHeader("Authorization") String authorization,
                                                     @Valid @RequestBody ReplaceRequest request) {
        var user = auth.requireAdmin(authorization);
        limit("admin-mfa-replace-account", user.getId().toString());
        return mfa.replace(user, request.password(), request.code().trim());
    }

    @PostMapping("/admin/accounts/{id}/mfa/reset")
    ResetResponse reset(@RequestHeader("Authorization") String authorization,
                                        @PathVariable Long id, @Valid @RequestBody ResetRequest request) {
        var user = auth.requirePermission(authorization, AdminPermission.ADMIN_MANAGE);
        limit("admin-mfa-reset-account", user.getId().toString());
        return new ResetResponse(mfa.reset(user, id, request.reason()));
    }

    private void limit(String scope, String key) {
        if (!limits.tryAcquire(scope, key, 10)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后重试");
        }
    }

    public record LoginRequest(@NotBlank @Pattern(regexp = "^1\\d{10}$") String phone,
                               @NotBlank @Size(max = 72) String password) { }
    public record VerifyRequest(@NotBlank @Size(max = 128) String challengeToken,
                                @NotBlank @Size(max = 128) String code,
                                @Size(max = 16) String clientType,
                                @Size(max = 80) String deviceName) {
        public VerifyRequest(String challengeToken, String code) {
            this(challengeToken, code, null, null);
        }
    }
    public record ReplaceRequest(@NotBlank @Size(max = 72) String password,
                                 @NotBlank @Size(max = 128) String code) { }
    public record ResetRequest(@NotBlank @Size(max = 200) String reason) { }
    public record ResetResponse(String resetGrant) { }
}
