package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.auth.SessionRepository;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRepository;
import com.zhangxuan.runruncampus.user.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {
    @Mock UserRepository userRepository;
    @Mock SessionRepository sessionRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AdminAuditService auditService;

    @Test
    void unlocksNonAdminUserAndWritesAuditLog() {
        AdminUserService service = new AdminUserService(
                userRepository, sessionRepository, passwordEncoder, auditService);
        AppUser administrator = user(1L, "超级管理员", UserRole.ADMIN);
        AppUser target = user(2L, "锁定用户", UserRole.CUSTOMER);
        target.recordLoginFailure(1, Instant.now().plusSeconds(900));
        when(userRepository.findLockedById(2L)).thenReturn(Optional.of(target));

        AdminUserController.AdminUserItem unlocked = service.unlockLogin(administrator, 2L);

        assertEquals(0, unlocked.failedLoginAttempts());
        assertEquals(null, unlocked.loginLockedUntil());
        verify(auditService).record(administrator, AdminAuditAction.USER_LOGIN_UNLOCKED,
                AdminAuditTargetType.USER, 2L, "解除用户登录锁定：锁定用户");
    }

    @Test
    void resetsCourierPasswordRevokesSessionsUnlocksLoginAndWritesAuditLog() {
        AdminUserService service = new AdminUserService(
                userRepository, sessionRepository, passwordEncoder, auditService);
        AppUser administrator = user(1L, "超级管理员", UserRole.ADMIN);
        AppUser courier = user(2L, "配送骑手", UserRole.COURIER);
        courier.recordLoginFailure(1, Instant.now().plusSeconds(900));
        when(userRepository.findLockedById(2L)).thenReturn(Optional.of(courier));
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        AdminUserController.AdminUserItem updated = service.resetPassword(
                administrator, 2L, "new-password");

        assertEquals("new-hash", courier.getPasswordHash());
        assertEquals(0, updated.failedLoginAttempts());
        assertNull(updated.loginLockedUntil());
        verify(sessionRepository).deleteByUser(courier);
        verify(auditService).record(administrator, AdminAuditAction.USER_PASSWORD_RESET,
                AdminAuditTargetType.USER, 2L, "重置用户密码：配送骑手（COURIER）");
    }

    private AppUser user(long id, String name, UserRole role) {
        AppUser user = new AppUser("1380000000" + id, "hash", name, role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
