package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.auth.SessionRepository;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRepository;
import com.zhangxuan.runruncampus.user.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminUserService {
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuditService auditService;

    public AdminUserService(UserRepository userRepository, SessionRepository sessionRepository,
                            PasswordEncoder passwordEncoder, AdminAuditService auditService) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public AdminUserController.AdminUserItem suspend(AppUser administrator, Long id) {
        AppUser user = mutableUser(id);
        if (!user.isEnabled()) return item(user);
        user.suspend();
        sessionRepository.deleteByUser(user);
        auditService.record(administrator, AdminAuditAction.USER_SUSPENDED, AdminAuditTargetType.USER,
                user.getId(), "停用用户账号：" + user.getDisplayName());
        return item(user);
    }

    @Transactional
    public AdminUserController.AdminUserItem restore(AppUser administrator, Long id) {
        AppUser user = mutableUser(id);
        if (user.isEnabled()) return item(user);
        user.restore();
        auditService.record(administrator, AdminAuditAction.USER_RESTORED, AdminAuditTargetType.USER,
                user.getId(), "恢复用户账号：" + user.getDisplayName());
        return item(user);
    }

    @Transactional
    public AdminUserController.AdminUserItem unlockLogin(AppUser administrator, Long id) {
        AppUser user = mutableUser(id);
        if (user.getFailedLoginAttempts() == 0 && user.getLoginLockedUntil() == null) return item(user);
        user.clearLoginFailures();
        auditService.record(administrator, AdminAuditAction.USER_LOGIN_UNLOCKED, AdminAuditTargetType.USER,
                user.getId(), "解除用户登录锁定：" + user.getDisplayName());
        return item(user);
    }

    @Transactional
    public AdminUserController.AdminUserItem resetPassword(AppUser administrator, Long id, String password) {
        AppUser user = mutableUser(id);
        user.changePasswordHash(passwordEncoder.encode(password));
        user.clearLoginFailures();
        sessionRepository.deleteByUser(user);
        auditService.record(administrator, AdminAuditAction.USER_PASSWORD_RESET, AdminAuditTargetType.USER,
                user.getId(), "重置用户密码：" + user.getDisplayName() + "（" + user.getRole().name() + "）");
        return item(user);
    }

    private AppUser mutableUser(Long id) {
        AppUser user = userRepository.findLockedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        if (user.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "管理员账号不能在用户目录中管理");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public AdminUserController.AdminUserPageResponse list(String query, UserRole role, int page, int size) {
        String normalizedQuery = normalize(query);
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<AppUser> users = normalizedQuery == null
                ? userRepository.filterForAdmin(role, pageable)
                : userRepository.searchForAdmin(normalizedQuery, role, pageable);
        return new AdminUserController.AdminUserPageResponse(
                users.getContent().stream().map(this::item).toList(),
                users.getNumber(), users.getSize(), users.getTotalElements(), users.getTotalPages());
    }

    private AdminUserController.AdminUserItem item(AppUser user) {
        return new AdminUserController.AdminUserItem(
                user.getId(), user.getDisplayName(), user.getPhone(), user.getRole(), user.isEnabled(),
                user.isCourierApproved(), user.getCreatedAt(), user.getFailedLoginAttempts(),
                user.getLoginLockedUntil());
    }

    private String normalize(String query) {
        if (query == null || query.isBlank()) return null;
        return query.trim();
    }
}
