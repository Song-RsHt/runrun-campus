package com.zhangxuan.runrunv2.controller.identity;

import com.zhangxuan.runrunv2.dto.identity.BindPhoneRequest;
import com.zhangxuan.runrunv2.dto.identity.LogoutRequest;
import com.zhangxuan.runrunv2.dto.identity.PasswordChangeRequest;
import com.zhangxuan.runrunv2.dto.identity.PasswordLoginRequest;
import com.zhangxuan.runrunv2.dto.identity.WechatLoginRequest;
import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.security.IdentityPermission;
import com.zhangxuan.runrunv2.security.IdentityWebConfiguration;
import com.zhangxuan.runrunv2.service.identity.IdentityService;
import com.zhangxuan.runrunv2.vo.identity.IdentityAccountVO;
import com.zhangxuan.runrunv2.vo.identity.IdentityLoginVO;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 新系统身份与会话 HTTP 入口；绑定请求并委托业务服务，不直接访问数据库。 */
@RestController
@RequestMapping("/api/v2")
public class IdentityController {

    private final IdentityService identity;

    public IdentityController(IdentityService identity) {
        this.identity = identity;
    }

    @PostMapping("/auth/wechat/login")
    @IdentityPermission(anonymous = true)
    public IdentityLoginVO login(@RequestBody WechatLoginRequest body) {
        return identity.login(body.code());
    }

    @PostMapping("/auth/wechat/bind-phone")
    @IdentityPermission(anonymous = true)
    public ResponseEntity<IdentityLoginVO> bind(@RequestBody BindPhoneRequest body) {
        return ResponseEntity.status(201)
                .body(
                        identity.bindPhone(
                                body.loginTicket(), body.phoneCode(), body.requestedRole()));
    }

    @PostMapping("/auth/password/login")
    @IdentityPermission(anonymous = true)
    public IdentityLoginVO passwordLogin(@RequestBody PasswordLoginRequest body) {
        return identity.passwordLogin(body.phone(), body.password());
    }

    @GetMapping("/auth/session")
    @IdentityPermission
    public IdentityAccountVO session(HttpServletRequest request) {
        return identity.authenticate(IdentityWebConfiguration.bearer(request));
    }

    @PostMapping("/auth/logout")
    @IdentityPermission
    public Map<String, Boolean> logout(
            HttpServletRequest request,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody LogoutRequest body) {
        identity.revoke(IdentityWebConfiguration.bearer(request), body.allSessions(), key);
        return Map.of("revoked", true);
    }

    @PutMapping("/auth/password")
    @IdentityPermission
    public Map<String, Boolean> password(
            HttpServletRequest request,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody PasswordChangeRequest body) {
        identity.setPassword(IdentityWebConfiguration.bearer(request), body.password(), key);
        return Map.of("changed", true);
    }

    @PatchMapping("/admin/access/{userId}")
    @IdentityPermission
    public void manageAccess() {
        // 阶段一仅通过受控主机命令开通管理员，普通 ADMIN 凭证不能授予管理员资格。
        throw new IdentityFailure(403, "ADMIN_ACCESS_MANAGEMENT_FORBIDDEN");
    }
}
