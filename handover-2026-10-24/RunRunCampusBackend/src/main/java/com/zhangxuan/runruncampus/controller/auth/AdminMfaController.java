package com.zhangxuan.runruncampus.controller.auth;

import com.zhangxuan.runruncampus.dto.request.auth.mfa.LoginRequest;
import com.zhangxuan.runruncampus.dto.request.auth.mfa.ReplaceRequest;
import com.zhangxuan.runruncampus.dto.request.auth.mfa.ResetRequest;
import com.zhangxuan.runruncampus.dto.request.auth.mfa.VerifyRequest;
import com.zhangxuan.runruncampus.dto.response.auth.mfa.ResetResponse;
import com.zhangxuan.runruncampus.enums.admin.AdminPermission;
import com.zhangxuan.runruncampus.security.ApiRateLimitService;
import com.zhangxuan.runruncampus.service.auth.AdminMfaService;
import com.zhangxuan.runruncampus.service.auth.AuthService;
import jakarta.validation.Valid;
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
    this.mfa = mfa;
    this.auth = auth;
    this.limits = limits;
  }

  @PostMapping("/auth/admin/login")
  public AdminMfaService.Challenge login(@Valid @RequestBody LoginRequest request) {
    limit("admin-mfa-login-account", request.phone());
    return mfa.begin(request.phone(), request.password());
  }

  @PostMapping("/auth/admin/verify")
  public AdminMfaService.Verification verify(@Valid @RequestBody VerifyRequest request) {
    limit("admin-mfa-challenge", request.challengeToken());
    return mfa.verify(
        request.challengeToken(),
        request.code().trim(),
        AuthService.SessionMetadata.of(request.clientType(), request.deviceName()));
  }

  @PostMapping("/admin/accounts/me/mfa/replace")
  public AdminMfaService.Challenge replace(
      @RequestHeader("Authorization") String authorization,
      @Valid @RequestBody ReplaceRequest request) {
    var user = auth.requireAdmin(authorization);
    limit("admin-mfa-replace-account", user.getId().toString());
    return mfa.replace(user, request.password(), request.code().trim());
  }

  @PostMapping("/admin/accounts/{id}/mfa/reset")
  public ResetResponse reset(
      @RequestHeader("Authorization") String authorization,
      @PathVariable Long id,
      @Valid @RequestBody ResetRequest request) {
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
