package com.zhangxuan.runruncampus.auth;

import com.zhangxuan.runruncampus.admin.AdminPermission;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRepository;
import com.zhangxuan.runruncampus.user.UserRole;
import com.zhangxuan.runruncampus.privacy.PrivacyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final LoginCaptchaService captchaService;
    private final int sessionHours;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository, SessionRepository sessionRepository, PasswordEncoder passwordEncoder,
                       LoginAttemptService loginAttemptService,
                       LoginCaptchaService captchaService,
                       @Value("${app.session-hours}") int sessionHours) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.captchaService = captchaService;
        this.sessionHours = sessionHours;
    }

    public Session register(String phone, String password, String displayName, UserRole role) {
        return register(phone, password, displayName, role, SessionMetadata.unknown());
    }

    public Session register(String phone, String password, String displayName, UserRole role, SessionMetadata metadata) {
        return register(phone, password, displayName, role, metadata, PrivacyService.CURRENT_VERSION);
    }

    public Session register(String phone, String password, String displayName, UserRole role,
                            SessionMetadata metadata, String privacyPolicyVersion) {
        PrivacyService.requireCurrentVersion(privacyPolicyVersion);
        if (role == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "管理员账号不能公开注册");
        }
        if (userRepository.findByPhone(phone).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该手机号已注册");
        }
        AppUser user = new AppUser(phone, passwordEncoder.encode(password), displayName, role);
        user.recordPrivacyConsent(privacyPolicyVersion);
        if (role == UserRole.COURIER) user.markNewCourierUnapproved();
        userRepository.save(user);
        return createSession(user, metadata);
    }

    public Session login(String phone, String password) {
        return login(phone, password, null, null, SessionMetadata.unknown());
    }

    public Session login(String phone, String password, String captchaToken, String captchaCode,
                         SessionMetadata metadata) {
        if (loginAttemptService.requiresCaptcha(phone)) captchaService.verify(captchaToken, captchaCode);
        AppUser user = userRepository.findByPhone(phone).orElse(null);
        if (user == null) {
            loginAttemptService.recordUnknownFailure(phone);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "手机号或密码错误");
        }
        if (user.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "管理员请通过运营后台完成双因素登录");
        }
        if (user.isLoginLocked(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "登录尝试过多，请稍后再试");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            loginAttemptService.recordFailure(user.getId());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "手机号或密码错误");
        }
        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "账号已停用，请联系平台管理员");
        }
        if (user.getFailedLoginAttempts() > 0 || user.getLoginLockedUntil() != null) {
            loginAttemptService.clearFailures(user.getId());
        }
        loginAttemptService.clearTransientFailures(phone);
        return createSession(user, metadata);
    }

    @Transactional(readOnly = true)
    public AppUser requireUser(String authorization) {
        return requireSession(authorization).getUser();
    }

    @Transactional(readOnly = true)
    public AppUser requireAdmin(String authorization) {
        AppUser user = requireUser(authorization);
        if (user.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可访问");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public AppUser requirePermission(String authorization, AdminPermission permission) {
        AppUser user = requireAdmin(authorization);
        if (user.getAdminRole() == null || !user.getAdminRole().permits(permission)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前管理员岗位无此权限");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public List<SessionInfo> sessions(String authorization) {
        Session current = requireSession(authorization);
        return sessionRepository.findByUserAndExpiresAtAfterOrderByCreatedAtDesc(current.getUser(), Instant.now())
                .stream().map(session -> new SessionInfo(session.getId(), session.getDeviceName(),
                        session.getClientType(), session.getCreatedAt(), session.getExpiresAt(),
                        session.getId().equals(current.getId()))).toList();
    }

    @Transactional
    public void revokeSession(String authorization, Long sessionId) {
        Session current = requireSession(authorization);
        if (current.getId().equals(sessionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前设备请使用退出登录");
        }
        Session target = sessionRepository.findByIdAndUser(sessionId, current.getUser())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "登录设备不存在"));
        sessionRepository.delete(target);
    }

    @Transactional
    public long revokeOtherSessions(String authorization) {
        Session current = requireSession(authorization);
        return sessionRepository.deleteByUserAndIdNot(current.getUser(), current.getId());
    }

    @Transactional
    public void logout(String authorization) {
        sessionRepository.delete(requireSession(authorization));
    }

    @Transactional
    public void changeOwnPassword(String authorization, String currentPassword, String newPassword) {
        AppUser user = requireSession(authorization).getUser();
        user = userRepository.findLockedById(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已失效"));
        if (user.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "管理员请在运营后台修改密码");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前密码错误");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码不能与当前密码相同");
        }
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        sessionRepository.deleteByUser(user);
    }

    private Session requireSession(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        String token = authorization.substring("Bearer ".length());
        Session session = sessionRepository.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已失效"));
        if (session.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已失效");
        }
        if (!session.getUser().isEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "账号已停用，请联系平台管理员");
        }
        if (session.getUser().getRole() == UserRole.ADMIN && (!session.isAdminMfaVerified()
                || session.getSecurityVersion() != session.getUser().getSecurityVersion())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请重新完成管理员双因素登录");
        }
        return session;
    }

    private Session createSession(AppUser user) {
        return createSession(user, SessionMetadata.unknown());
    }

    private Session createSession(AppUser user, SessionMetadata metadata) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return sessionRepository.save(new Session(token, user, Instant.now().plusSeconds(sessionHours * 3600L),
                metadata.clientType(), metadata.deviceName()));
    }

    public record SessionMetadata(SessionClientType clientType, String deviceName) {
        public static SessionMetadata of(String clientType, String deviceName) {
            SessionClientType type = SessionClientType.from(clientType);
            String normalizedName = deviceName == null || deviceName.isBlank() ? "未知设备" : deviceName;
            return new SessionMetadata(type, normalizedName);
        }
        public static SessionMetadata unknown() { return new SessionMetadata(SessionClientType.UNKNOWN, "未知设备"); }
    }

    public record SessionInfo(Long id, String deviceName, SessionClientType clientType, Instant createdAt,
                              Instant expiresAt, boolean current) { }
}
