package com.zhangxuan.runruncampus.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.zhangxuan.runruncampus.entity.user.AppUser;
import com.zhangxuan.runruncampus.enums.user.UserRole;
import com.zhangxuan.runruncampus.repository.user.UserRepository;
import com.zhangxuan.runruncampus.service.impl.auth.LoginAttemptServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock UserRepository userRepository;

    private LoginAttemptService service;

    private AppUser user;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptServiceImpl(userRepository, 5, 15);
        user = new AppUser("13800000001", "hash", "测试用户", UserRole.CUSTOMER);
        ReflectionTestUtils.setField(user, "id", 1L);
        Mockito.lenient().when(userRepository.findLockedById(1L)).thenReturn(Optional.of(user));
    }

    @Test
    void locksAccountAfterConfiguredNumberOfFailures() {
        for (int attempt = 0; attempt < 5; attempt++) {
            service.recordFailure(1L);
        }
        assertEquals(5, user.getFailedLoginAttempts());
        assertTrue(user.isLoginLocked(Instant.now()));
    }

    @Test
    void successfulLoginClearsPreviousFailures() {
        user.recordLoginFailure(5, Instant.now().plusSeconds(900));
        service.clearFailures(1L);
        assertEquals(0, user.getFailedLoginAttempts());
        assertEquals(null, user.getLoginLockedUntil());
    }

    @Test
    void unknownAccountsAlsoRequireCaptchaAfterThreeFailures() {
        when(userRepository.findByPhone("13800000009")).thenReturn(Optional.empty());
        for (int attempt = 0; attempt < 3; attempt++) {
            service.recordUnknownFailure("13800000009");
        }
        assertTrue(service.requiresCaptcha("13800000009"));
    }
}
