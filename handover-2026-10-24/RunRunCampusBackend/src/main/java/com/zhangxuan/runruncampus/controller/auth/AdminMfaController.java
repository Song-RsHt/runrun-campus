package com.zhangxuan.runruncampus.controller.auth;

import com.zhangxuan.runruncampus.dto.auth.AdminMfaLoginRequest;
import com.zhangxuan.runruncampus.dto.auth.AdminMfaReplaceRequest;
import com.zhangxuan.runruncampus.dto.auth.AdminMfaResetRequest;
import com.zhangxuan.runruncampus.dto.auth.AdminMfaVerifyRequest;
import com.zhangxuan.runruncampus.enums.admin.AdminPermission;
import com.zhangxuan.runruncampus.model.auth.AdminMfaVerification;
import com.zhangxuan.runruncampus.model.auth.SessionMetadata;
import com.zhangxuan.runruncampus.security.ApiRateLimitService;
import com.zhangxuan.runruncampus.service.auth.AdminMfaService;
import com.zhangxuan.runruncampus.service.auth.AuthService;
import com.zhangxuan.runruncampus.vo.auth.AdminMfaChallengeVO;
import com.zhangxuan.runruncampus.vo.auth.ResetResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 管理员双因素认证 HTTP 入口；绑定请求并委托业务服务，不直接访问数据库。 */
@RestController
@RequestMapping("/api/v1")
public class AdminMfaController {

    private final AdminMfaService mfa;
    private final AuthService auth;
    private final ApiRateLimitService limits;

    public AdminMfaController(AdminMfaService mfa, AuthService auth, ApiRateLimitService limits) {
        this.mfa = mfa;
        this.auth = auth;
        this.limits = limits;
    }

    @PostMapping("/auth/admin/login")
    public AdminMfaChallengeVO login(@Valid @RequestBody AdminMfaLoginRequest request) {
        limit("admin-mfa-login-account", request.phone());
        return mfa.begin(request.phone(), request.password());
    }

    @PostMapping("/auth/admin/verify")
    public AdminMfaVerification verify(@Valid @RequestBody AdminMfaVerifyRequest request) {
        limit("admin-mfa-challenge", request.challengeToken());
        return mfa.verify(
                request.challengeToken(),
                request.code().trim(),
                SessionMetadata.of(request.clientType(), request.deviceName()));
    }

    @PostMapping("/admin/accounts/me/mfa/replace")
    public AdminMfaChallengeVO replace(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody AdminMfaReplaceRequest request) {
        var user = auth.requireAdmin(authorization);
        limit("admin-mfa-replace-account", user.getId().toString());
        return mfa.replace(user, request.password(), request.code().trim());
    }

    @PostMapping("/admin/accounts/{id}/mfa/reset")
    public ResetResponse reset(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id,
            @Valid @RequestBody AdminMfaResetRequest request) {
        var user = auth.requirePermission(authorization, AdminPermission.ADMIN_MANAGE);
        limit("admin-mfa-reset-account", user.getId().toString());
        return new ResetResponse(mfa.reset(user, id, request.reason()));
    }

    private void limit(String scope, String key) {
        if (!limits.tryAcquire(scope, key, 10)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后重试");
        }
    }
}
