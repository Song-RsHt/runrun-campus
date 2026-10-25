package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.auth.SessionRepository;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRepository;
import com.zhangxuan.runruncampus.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAccountServiceTest {
    @Mock UserRepository userRepository;
    @Mock SessionRepository sessionRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AdminAuditService auditService;

    private AdminAccountService service;
    private AppUser operator;

    @BeforeEach
    void setUp() {
        service = new AdminAccountService(userRepository, sessionRepository, passwordEncoder, auditService);
        operator = admin(1L, "超级管理员");
    }

    @Test
    void createsAdministratorWithSelectedRole() {
        when(userRepository.findByPhone("13800000002")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password88")).thenReturn("encoded");
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 2L);
            return user;
        });

        AdminAccountController.AdminAccountItem created = service.create(
                operator, "13800000002", "password88", "客服二号", AdminRole.SUPPORT);

        assertEquals(AdminRole.SUPPORT, created.adminRole());
        verify(auditService).record(operator, AdminAuditAction.ADMIN_CREATED,
                AdminAuditTargetType.ADMIN_ACCOUNT, 2L, "创建管理员：客服二号，岗位：SUPPORT");
    }

    @Test
    void changingRoleRevokesExistingSessions() {
        AppUser target = admin(2L, "运营员");
        when(userRepository.findLockedById(2L)).thenReturn(Optional.of(target));

        AdminAccountController.AdminAccountItem changed = service.changeRole(operator, 2L, AdminRole.AUDITOR);

        assertEquals(AdminRole.AUDITOR, changed.adminRole());
        verify(sessionRepository).deleteByUser(target);
    }

    @Test
    void cannotChangeOwnRole() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.changeRole(operator, 1L, AdminRole.SUPPORT));
    }

    @Test
    void suspendingAdministratorRevokesSessionsAndWritesAuditLog() {
        AppUser target = admin(2L, "离职管理员");
        when(userRepository.findLockedById(2L)).thenReturn(Optional.of(target));

        AdminAccountController.AdminAccountItem changed = service.suspend(operator, 2L);

        assertEquals(false, changed.enabled());
        verify(sessionRepository).deleteByUser(target);
        verify(auditService).record(operator, AdminAuditAction.ADMIN_SUSPENDED,
                AdminAuditTargetType.ADMIN_ACCOUNT, 2L, "停用管理员：离职管理员");
    }

    @Test
    void cannotSuspendOwnAccount() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.suspend(operator, 1L));
    }

    @Test
    void resettingPasswordReplacesHashAndRevokesSessions() {
        AppUser target = admin(2L, "忘记密码管理员");
        when(userRepository.findLockedById(2L)).thenReturn(Optional.of(target));
        when(passwordEncoder.encode("new-password88")).thenReturn("new-hash");

        service.resetPassword(operator, 2L, "new-password88");

        assertEquals("new-hash", target.getPasswordHash());
        verify(sessionRepository).deleteByUser(target);
        verify(auditService).record(operator, AdminAuditAction.ADMIN_PASSWORD_RESET,
                AdminAuditTargetType.ADMIN_ACCOUNT, 2L, "重置管理员密码：忘记密码管理员");
    }

    @Test
    void changingOwnPasswordChecksCurrentPasswordAndRevokesSessions() {
        when(userRepository.findLockedById(1L)).thenReturn(Optional.of(operator));
        when(passwordEncoder.matches("old-password", "hash")).thenReturn(true);
        when(passwordEncoder.matches("new-password88", "hash")).thenReturn(false);
        when(passwordEncoder.encode("new-password88")).thenReturn("changed-hash");

        service.changeOwnPassword(operator, "old-password", "new-password88");

        assertEquals("changed-hash", operator.getPasswordHash());
        verify(sessionRepository).deleteByUser(operator);
        verify(auditService).record(operator, AdminAuditAction.ADMIN_PASSWORD_CHANGED,
                AdminAuditTargetType.ADMIN_ACCOUNT, 1L, "管理员修改自己的密码");
    }

    @Test
    void changingOwnPasswordRejectsWrongCurrentPassword() {
        when(userRepository.findLockedById(1L)).thenReturn(Optional.of(operator));
        when(passwordEncoder.matches("wrong-password", "hash")).thenReturn(false);

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.changeOwnPassword(operator, "wrong-password", "new-password88"));
    }

    @Test
    void changingOwnPasswordUpdatesStoredAccountInsteadOfAuthenticationSnapshot() {
        PasswordEncoder encoder = new BCryptPasswordEncoder(4);
        String originalHash = encoder.encode("current-password");
        AppUser stored = admin(1L, "超级管理员");
        stored.changePasswordHash(originalHash);
        operator.changePasswordHash(originalHash);
        when(userRepository.findLockedById(1L)).thenReturn(Optional.of(stored));
        AdminAccountService accountService = new AdminAccountService(
                userRepository, sessionRepository, encoder, auditService);

        accountService.changeOwnPassword(operator, "current-password", "new-password88");

        assertTrue(encoder.matches("new-password88", stored.getPasswordHash()));
        assertFalse(encoder.matches("current-password", stored.getPasswordHash()));
        assertEquals(originalHash, operator.getPasswordHash());
        verify(sessionRepository).deleteByUser(stored);
        verify(auditService).record(stored, AdminAuditAction.ADMIN_PASSWORD_CHANGED,
                AdminAuditTargetType.ADMIN_ACCOUNT, 1L, "管理员修改自己的密码");
    }

    @Test
    void unlocksOtherAdministratorAndWritesAuditLog() {
        AppUser target = admin(2L, "锁定管理员");
        target.recordLoginFailure(1, java.time.Instant.now().plusSeconds(900));
        when(userRepository.findLockedById(2L)).thenReturn(Optional.of(target));

        AdminAccountController.AdminAccountItem unlocked = service.unlockLogin(operator, 2L);

        assertEquals(0, unlocked.failedLoginAttempts());
        assertEquals(null, unlocked.loginLockedUntil());
        verify(auditService).record(operator, AdminAuditAction.ADMIN_LOGIN_UNLOCKED,
                AdminAuditTargetType.ADMIN_ACCOUNT, 2L, "解除管理员登录锁定：锁定管理员");
    }

    private AppUser admin(long id, String name) {
        AppUser user = new AppUser("1380000000" + id, "hash", name, UserRole.ADMIN);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
