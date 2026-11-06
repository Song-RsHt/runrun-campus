package com.zhangxuan.runruncampus.service.impl;

import com.zhangxuan.runruncampus.entity.AdminMfaCredential;
import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.Session;
import com.zhangxuan.runruncampus.enums.AdminAuditAction;
import com.zhangxuan.runruncampus.enums.AdminAuditTargetType;
import com.zhangxuan.runruncampus.enums.AdminRole;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.exception.BusinessFailure;
import com.zhangxuan.runruncampus.model.AdminMfaVerification;
import com.zhangxuan.runruncampus.model.SessionMetadata;
import com.zhangxuan.runruncampus.repository.AdminMfaRepository;
import com.zhangxuan.runruncampus.repository.SessionRepository;
import com.zhangxuan.runruncampus.repository.UserRepository;
import com.zhangxuan.runruncampus.security.AdminMfaCrypto;
import com.zhangxuan.runruncampus.service.AdminAuditService;
import com.zhangxuan.runruncampus.service.AdminMfaService;
import com.zhangxuan.runruncampus.vo.AdminMfaChallengeVO;
import com.zhangxuan.runruncampus.vo.AuthResponse;
import com.zhangxuan.runruncampus.vo.UserResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 管理员双因素认证业务实现；通过注入的业务契约及数据访问组件执行，事务边界保留在本类。
 *
 * <p>双因素失败计数与一次性挑战消费沿用原不回滚规则；业务异常使用类型声明，来自加密协作者的既有 Web 异常按完整类名保留匹配。
 */
@Service
public class AdminMfaServiceImpl implements AdminMfaService {

    private final UserRepository users;
    private final AdminMfaRepository credentials;
    private final SessionRepository sessions;
    private final PasswordEncoder passwords;
    private final AdminMfaCrypto crypto;
    private final AdminAuditService audit;
    private final int sessionHours;

    public AdminMfaServiceImpl(
            UserRepository users,
            AdminMfaRepository credentials,
            SessionRepository sessions,
            PasswordEncoder passwords,
            AdminMfaCrypto crypto,
            AdminAuditService audit,
            @Value("${app.session-hours}") int sessionHours) {
        this.users = users;
        this.credentials = credentials;
        this.sessions = sessions;
        this.passwords = passwords;
        this.crypto = crypto;
        this.audit = audit;
        this.sessionHours = sessionHours;
    }

    @Transactional(
            noRollbackFor = BusinessFailure.class,
            noRollbackForClassName = "org.springframework.web.server.ResponseStatusException")
    @Override
    public AdminMfaChallengeVO begin(String phone, String password) {
        crypto.requireConfigured();
        AppUser user =
                users.findLockedByPhone(phone)
                        .filter(value -> value.getRole() == UserRole.ADMIN)
                        .orElseThrow(() -> rejected("手机号或密码错误"));
        requireAvailable(user);
        if (!passwords.matches(password, user.getPasswordHash())) {
            fail(user, null, "手机号或密码错误");
        }
        AdminMfaCredential state = state(user);
        return challenge(
                user,
                state,
                state.resetGrantHash != null
                        ? "RECOVER"
                        : state.secretCiphertext == null ? "ENROLL" : "VERIFY");
    }

    @Transactional(
            noRollbackFor = BusinessFailure.class,
            noRollbackForClassName = "org.springframework.web.server.ResponseStatusException")
    @Override
    public AdminMfaVerification verify(String token, String code) {
        return verify(token, code, SessionMetadata.unknown());
    }

    @Transactional(
            noRollbackFor = BusinessFailure.class,
            noRollbackForClassName = "org.springframework.web.server.ResponseStatusException")
    @Override
    public AdminMfaVerification verify(String token, String code, SessionMetadata metadata) {
        crypto.requireConfigured();
        String hash = AdminMfaCrypto.hash(token);
        Long id =
                credentials
                        .findUserIdByChallengeHash(hash)
                        .orElseThrow(() -> rejected("验证已失效，请重新登录"));
        AppUser user = users.findLockedById(id).orElseThrow(() -> rejected("验证已失效，请重新登录"));
        requireAvailable(user);
        // 先锁账号再读取凭据，确保并发请求能看到已提交的消费状态。
        AdminMfaCredential state = state(user);
        if (!AdminMfaCrypto.matches(state.challengeHash, hash)
                || state.challengeExpiresAt == null
                || !state.challengeExpiresAt.isAfter(Instant.now())
                || state.challengeAttempts >= 5
                || state.challengeVersion != user.getSecurityVersion()) {
            throw rejected("验证已失效，请重新登录");
        }
        if ("RECOVER".equals(state.challengeKind)) {
            if (state.resetGrantExpiresAt == null
                    || !state.resetGrantExpiresAt.isAfter(Instant.now())) {
                throw rejected("重置凭证已过期，请联系管理员重新核验");
            }
            if (!AdminMfaCrypto.matches(state.resetGrantHash, AdminMfaCrypto.hash(code))) {
                fail(user, state, "重置凭证错误");
            }
            // 注册成功前保留授权，避免中断后无法继续绑定。
            return new AdminMfaVerification(null, List.of(), challenge(user, state, "ENROLL"));
        }
        List<String> recoveryCodes = List.of();
        if ("ENROLL".equals(state.challengeKind) || "REPLACE".equals(state.challengeKind)) {
            String secret = crypto.decrypt(id, state.pendingSecretCiphertext);
            long step =
                    AdminMfaCrypto.matchStep(secret, code, Instant.now().getEpochSecond() / 30, -1);
            if (step < 0) {
                fail(user, state, "动态码错误或已过期");
            }
            boolean replacing = state.secretCiphertext != null;
            state.secretCiphertext = state.pendingSecretCiphertext;
            state.lastUsedStep = step;
            state.resetGrantHash = null;
            state.resetGrantExpiresAt = null;
            recoveryCodes = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                recoveryCodes.add(AdminMfaCrypto.randomToken());
            }
            state.recoveryHashes =
                    String.join("\n", recoveryCodes.stream().map(AdminMfaCrypto::hash).toList());
            user.invalidateAdminAuthentication();
            sessions.deleteByUser(user);
            record(
                    user,
                    replacing
                            ? AdminAuditAction.ADMIN_MFA_REPLACED
                            : AdminAuditAction.ADMIN_MFA_ENROLLED);
        } else {
            consumeFactor(user, state, code);
        }
        state.clearChallenge();
        user.clearLoginFailures();
        Session session =
                new Session(
                        AdminMfaCrypto.randomToken(),
                        user,
                        Instant.now().plusSeconds(sessionHours * 3600L),
                        metadata.clientType(),
                        metadata.deviceName());
        session.markAdminMfaVerified();
        sessions.save(session);
        record(user, AdminAuditAction.ADMIN_MFA_LOGIN_SUCCEEDED);
        return new AdminMfaVerification(response(session), recoveryCodes, null);
    }

    @Transactional(
            noRollbackFor = BusinessFailure.class,
            noRollbackForClassName = "org.springframework.web.server.ResponseStatusException")
    @Override
    public AdminMfaChallengeVO replace(AppUser authenticated, String password, String code) {
        crypto.requireConfigured();
        AppUser user =
                users.findLockedById(authenticated.getId()).orElseThrow(() -> rejected("登录已失效"));
        requireAvailable(user);
        if (authenticated.getSecurityVersion() != user.getSecurityVersion()) {
            throw rejected("登录已失效");
        }
        AdminMfaCredential state = state(user);
        if (!passwords.matches(password, user.getPasswordHash())) {
            fail(user, null, "当前密码错误");
        }
        consumeFactor(user, state, code);
        return challenge(user, state, "REPLACE");
    }

    @Transactional
    @Override
    public String reset(AppUser authenticated, Long targetId, String reason) {
        // 涉及两个账号时采用固定锁顺序，避免相互等待。
        List<AppUser> locked =
                List.of(authenticated.getId(), targetId).stream()
                        .distinct()
                        .sorted()
                        .map(id -> users.findLockedById(id).orElseThrow(() -> rejected("管理员不存在")))
                        .toList();
        AppUser operator =
                locked.stream()
                        .filter(user -> user.getId().equals(authenticated.getId()))
                        .findFirst()
                        .orElseThrow();
        AppUser target =
                locked.stream()
                        .filter(user -> user.getId().equals(targetId))
                        .findFirst()
                        .orElseThrow();
        requireAvailable(operator);
        if (operator.getSecurityVersion() != authenticated.getSecurityVersion()
                || operator.getAdminRole() != AdminRole.SUPER_ADMIN) {
            throw new BusinessFailure(403, "仅超级管理员可重置认证器");
        }
        if (target.getRole() != UserRole.ADMIN || target.getAdminRole() == AdminRole.SUPER_ADMIN) {
            throw new BusinessFailure(409, "超级管理员认证器恢复请使用受审计的离线流程");
        }
        String grant = resetState(target);
        audit.record(
                operator,
                AdminAuditAction.ADMIN_MFA_RESET,
                AdminAuditTargetType.ADMIN_ACCOUNT,
                targetId,
                "人工核验后重置管理员认证器：" + reason.trim());
        return grant;
    }

    @Override
    public String resetState(AppUser target) {
        AdminMfaCredential state = state(target);
        String grant = AdminMfaCrypto.randomToken();
        state.secretCiphertext = null;
        state.lastUsedStep = -1;
        state.recoveryHashes = "";
        state.resetGrantHash = AdminMfaCrypto.hash(grant);
        state.resetGrantExpiresAt = Instant.now().plusSeconds(86400);
        state.clearChallenge();
        target.invalidateAdminAuthentication();
        target.clearLoginFailures();
        sessions.deleteByUser(target);
        return grant;
    }

    private AdminMfaChallengeVO challenge(AppUser user, AdminMfaCredential state, String kind) {
        String token = AdminMfaCrypto.randomToken();
        state.clearChallenge();
        state.challengeHash = AdminMfaCrypto.hash(token);
        state.challengeExpiresAt = Instant.now().plusSeconds(300);
        state.challengeVersion = user.getSecurityVersion();
        state.challengeKind = kind;
        String secret = null;
        String uri = null;
        if ("ENROLL".equals(kind) || "REPLACE".equals(kind)) {
            secret = AdminMfaCrypto.newSecret();
            state.pendingSecretCiphertext = crypto.encrypt(user.getId(), secret);
            uri =
                    "otpauth://totp/"
                            + URLEncoder.encode(
                                    "RunRun Campus:" + user.getPhone(), StandardCharsets.UTF_8)
                            + "?secret="
                            + secret
                            + "&issuer=RunRun%20Campus&algorithm=SHA1&digits=6&period=30";
        }
        credentials.save(state);
        return new AdminMfaChallengeVO(kind, token, state.challengeExpiresAt, secret, uri);
    }

    private void consumeFactor(AppUser user, AdminMfaCredential state, String code) {
        if (state.secretCiphertext == null) {
            throw rejected("请先绑定认证器");
        }
        long step =
                AdminMfaCrypto.matchStep(
                        crypto.decrypt(user.getId(), state.secretCiphertext),
                        code,
                        Instant.now().getEpochSecond() / 30,
                        state.lastUsedStep);
        if (step >= 0) {
            state.lastUsedStep = step;
            return;
        }
        String hash = AdminMfaCrypto.hash(code);
        List<String> hashes = new ArrayList<>(Arrays.asList(state.recoveryHashes.split("\n")));
        if (hashes.removeIf(value -> AdminMfaCrypto.matches(value, hash))) {
            state.recoveryHashes = String.join("\n", hashes);
            record(user, AdminAuditAction.ADMIN_MFA_RECOVERY_USED);
            return;
        }
        fail(user, state, "动态码或恢复码错误、过期或已使用");
    }

    private AdminMfaCredential state(AppUser user) {
        return credentials
                .findById(user.getId())
                .orElseGet(() -> credentials.save(new AdminMfaCredential(user.getId())));
    }

    private void requireAvailable(AppUser user) {
        if (user.getRole() != UserRole.ADMIN || !user.isEnabled()) {
            throw rejected("管理员账号不可用");
        }
        if (user.isLoginLocked(Instant.now())) {
            throw new BusinessFailure(429, "登录尝试过多，请稍后再试");
        }
    }

    private void fail(AppUser user, AdminMfaCredential state, String message) {
        if (state != null) {
            state.challengeAttempts++;
        }
        user.recordLoginFailure(5, Instant.now().plusSeconds(900));
        record(user, AdminAuditAction.ADMIN_MFA_LOGIN_FAILED);
        throw rejected(message);
    }

    private void record(AppUser user, AdminAuditAction action) {
        audit.record(
                user,
                action,
                AdminAuditTargetType.ADMIN_ACCOUNT,
                user.getId(),
                "管理员双因素认证：" + action.name());
    }

    private BusinessFailure rejected(String message) {
        return new BusinessFailure(401, message);
    }

    private AuthResponse response(Session session) {
        AppUser user = session.getUser();
        return new AuthResponse(
                session.getToken(),
                new UserResponse(
                        user.getId(),
                        user.getPhone(),
                        user.getDisplayName(),
                        user.getRole(),
                        user.getAdminRole(),
                        user.getAdminRole().getPermissions(),
                        user.getPrivacyPolicyVersion(),
                        user.getPrivacyConsentAt()));
    }
}
