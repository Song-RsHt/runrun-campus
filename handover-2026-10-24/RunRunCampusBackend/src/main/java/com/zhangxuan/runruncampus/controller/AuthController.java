package com.zhangxuan.runruncampus.controller;

import com.zhangxuan.runruncampus.dto.ChangePasswordRequest;
import com.zhangxuan.runruncampus.dto.LoginRequest;
import com.zhangxuan.runruncampus.dto.RegisterRequest;
import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.Session;
import com.zhangxuan.runruncampus.model.SessionMetadata;
import com.zhangxuan.runruncampus.security.ApiRateLimitService;
import com.zhangxuan.runruncampus.service.AuthService;
import com.zhangxuan.runruncampus.service.LoginCaptchaService;
import com.zhangxuan.runruncampus.vo.AuthResponse;
import com.zhangxuan.runruncampus.vo.LoginCaptchaVO;
import com.zhangxuan.runruncampus.vo.RevokeResponse;
import com.zhangxuan.runruncampus.vo.SessionInfoVO;
import com.zhangxuan.runruncampus.vo.UserResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

/** 旧系统账号与会话 HTTP 入口；绑定请求并委托业务服务，不直接访问数据库。 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final ApiRateLimitService rateLimitService;
    private final LoginCaptchaService captchaService;

    public AuthController(
            AuthService authService,
            ApiRateLimitService rateLimitService,
            LoginCaptchaService captchaService) {
        this.authService = authService;
        this.rateLimitService = rateLimitService;
        this.captchaService = captchaService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return response(
                authService.register(
                        request.phone(),
                        request.password(),
                        request.displayName(),
                        request.role(),
                        SessionMetadata.of(request.clientType(), request.deviceName()),
                        request.privacyPolicyVersion()));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        if (!rateLimitService.tryAcquire("auth-login-account", request.phone(), 10)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
        }
        return response(
                authService.login(
                        request.phone(),
                        request.password(),
                        request.captchaToken(),
                        request.captchaCode(),
                        SessionMetadata.of(request.clientType(), request.deviceName())));
    }

    @PostMapping("/captcha")
    public LoginCaptchaVO captcha() {
        return captchaService.issue();
    }

    @GetMapping("/sessions")
    public List<SessionInfoVO> sessions(@RequestHeader("Authorization") String authorization) {
        return authService.sessions(authorization);
    }

    @GetMapping("/me")
    public UserResponse me(@RequestHeader("Authorization") String authorization) {
        return userResponse(authService.requireUser(authorization));
    }

    @DeleteMapping("/sessions/{id}")
    public RevokeResponse revoke(
            @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        authService.revokeSession(authorization, id);
        return new RevokeResponse(1);
    }

    @DeleteMapping("/sessions/others")
    public RevokeResponse revokeOthers(@RequestHeader("Authorization") String authorization) {
        return new RevokeResponse(authService.revokeOtherSessions(authorization));
    }

    @PostMapping("/logout")
    public RevokeResponse logout(@RequestHeader("Authorization") String authorization) {
        authService.logout(authorization);
        return new RevokeResponse(1);
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody ChangePasswordRequest request) {
        AppUser user = authService.requireUser(authorization);
        if (!rateLimitService.tryAcquire("auth-change-password", user.getId().toString(), 5)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
        }
        authService.changeOwnPassword(
                authorization, request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    private AuthResponse response(Session session) {
        return new AuthResponse(session.getToken(), userResponse(session.getUser()));
    }

    private UserResponse userResponse(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getPhone(),
                user.getDisplayName(),
                user.getRole(),
                user.getAdminRole(),
                user.getAdminRole() == null ? Set.of() : user.getAdminRole().getPermissions(),
                user.getPrivacyPolicyVersion(),
                user.getPrivacyConsentAt());
    }
}
