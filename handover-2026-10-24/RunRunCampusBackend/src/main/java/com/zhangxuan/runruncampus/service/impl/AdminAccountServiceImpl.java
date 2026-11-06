package com.zhangxuan.runruncampus.service.impl;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.enums.AdminAuditAction;
import com.zhangxuan.runruncampus.enums.AdminAuditTargetType;
import com.zhangxuan.runruncampus.enums.AdminRole;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.repository.SessionRepository;
import com.zhangxuan.runruncampus.repository.UserRepository;
import com.zhangxuan.runruncampus.service.AdminAuditService;
import com.zhangxuan.runruncampus.vo.AdminAccountItem;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.zhangxuan.runruncampus.exception.BusinessFailure;
import com.zhangxuan.runruncampus.service.AdminAccountService;

@Service
public class AdminAccountServiceImpl implements AdminAccountService {

    private final UserRepository userRepository;

    private final SessionRepository sessionRepository;

    private final PasswordEncoder passwordEncoder;

    private final AdminAuditService auditService;

    public AdminAccountServiceImpl(UserRepository userRepository, SessionRepository sessionRepository, PasswordEncoder passwordEncoder, AdminAuditService auditService) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    @Override
    public List<AdminAccountItem> list() {
        return userRepository.findByRoleOrderByCreatedAtAsc(UserRole.ADMIN).stream().map(this::item).toList();
    }

    @Transactional
    @Override
    public AdminAccountItem create(AppUser creator, String phone, String password, String displayName, AdminRole role) {
        if (userRepository.findByPhone(phone).isPresent()) {
            throw new BusinessFailure(409, "该手机号已注册");
        }
        AppUser administrator = new AppUser(phone, passwordEncoder.encode(password), displayName.trim(), UserRole.ADMIN);
        administrator.changeAdminRole(role);
        userRepository.save(administrator);
        auditService.record(creator, AdminAuditAction.ADMIN_CREATED, AdminAuditTargetType.ADMIN_ACCOUNT, administrator.getId(), "创建管理员：" + administrator.getDisplayName() + "，岗位：" + role.name());
        return item(administrator);
    }

    @Transactional
    @Override
    public AdminAccountItem changeRole(AppUser operator, Long id, AdminRole role) {
        AppUser administrator = requireOtherAdministrator(operator, id, "不能修改自己的管理员岗位");
        administrator.changeAdminRole(role);
        sessionRepository.deleteByUser(administrator);
        auditService.record(operator, AdminAuditAction.ADMIN_ROLE_CHANGED, AdminAuditTargetType.ADMIN_ACCOUNT, administrator.getId(), "调整管理员岗位：" + administrator.getDisplayName() + " → " + role.name());
        return item(administrator);
    }

    @Transactional
    @Override
    public AdminAccountItem suspend(AppUser operator, Long id) {
        AppUser administrator = requireOtherAdministrator(operator, id, "不能停用自己的管理员账号");
        if (!administrator.isEnabled())
            return item(administrator);
        administrator.suspend();
        sessionRepository.deleteByUser(administrator);
        auditService.record(operator, AdminAuditAction.ADMIN_SUSPENDED, AdminAuditTargetType.ADMIN_ACCOUNT, administrator.getId(), "停用管理员：" + administrator.getDisplayName());
        return item(administrator);
    }

    @Transactional
    @Override
    public AdminAccountItem restore(AppUser operator, Long id) {
        AppUser administrator = requireOtherAdministrator(operator, id, "不能恢复自己的管理员账号");
        if (administrator.isEnabled())
            return item(administrator);
        administrator.restore();
        auditService.record(operator, AdminAuditAction.ADMIN_RESTORED, AdminAuditTargetType.ADMIN_ACCOUNT, administrator.getId(), "恢复管理员：" + administrator.getDisplayName());
        return item(administrator);
    }

    @Transactional
    @Override
    public AdminAccountItem resetPassword(AppUser operator, Long id, String password) {
        AppUser administrator = requireOtherAdministrator(operator, id, "不能通过此入口重置自己的密码");
        administrator.changePasswordHash(passwordEncoder.encode(password));
        sessionRepository.deleteByUser(administrator);
        auditService.record(operator, AdminAuditAction.ADMIN_PASSWORD_RESET, AdminAuditTargetType.ADMIN_ACCOUNT, administrator.getId(), "重置管理员密码：" + administrator.getDisplayName());
        return item(administrator);
    }

    @Transactional
    @Override
    public AdminAccountItem unlockLogin(AppUser operator, Long id) {
        AppUser administrator = requireOtherAdministrator(operator, id, "不能通过此入口解锁自己的账号");
        if (administrator.getFailedLoginAttempts() == 0 && administrator.getLoginLockedUntil() == null) {
            return item(administrator);
        }
        administrator.clearLoginFailures();
        auditService.record(operator, AdminAuditAction.ADMIN_LOGIN_UNLOCKED, AdminAuditTargetType.ADMIN_ACCOUNT, administrator.getId(), "解除管理员登录锁定：" + administrator.getDisplayName());
        return item(administrator);
    }

    @Transactional
    @Override
    public void changeOwnPassword(AppUser administrator, String currentPassword, String newPassword) {
        administrator = userRepository.findLockedById(administrator.getId()).orElseThrow(() -> new BusinessFailure(404, "管理员不存在"));
        if (!passwordEncoder.matches(currentPassword, administrator.getPasswordHash())) {
            throw new BusinessFailure(400, "当前密码错误");
        }
        if (passwordEncoder.matches(newPassword, administrator.getPasswordHash())) {
            throw new BusinessFailure(400, "新密码不能与当前密码相同");
        }
        administrator.changePasswordHash(passwordEncoder.encode(newPassword));
        sessionRepository.deleteByUser(administrator);
        auditService.record(administrator, AdminAuditAction.ADMIN_PASSWORD_CHANGED, AdminAuditTargetType.ADMIN_ACCOUNT, administrator.getId(), "管理员修改自己的密码");
    }

    private AppUser requireOtherAdministrator(AppUser operator, Long id, String selfMessage) {
        if (operator.getId().equals(id)) {
            throw new BusinessFailure(409, selfMessage);
        }
        return userRepository.findLockedById(id).filter(user -> user.getRole() == UserRole.ADMIN).orElseThrow(() -> new BusinessFailure(404, "管理员不存在"));
    }

    private AdminAccountItem item(AppUser user) {
        return new AdminAccountItem(user.getId(), user.getPhone(), user.getDisplayName(), user.getAdminRole(), user.getAdminRole().getPermissions(), user.isEnabled(), user.getCreatedAt(), user.getFailedLoginAttempts(), user.getLoginLockedUntil());
    }
}
