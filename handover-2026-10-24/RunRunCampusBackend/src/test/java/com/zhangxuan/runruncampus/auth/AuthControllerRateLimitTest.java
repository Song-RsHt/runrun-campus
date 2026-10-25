package com.zhangxuan.runruncampus.auth;

import com.zhangxuan.runruncampus.api.ApiRateLimitService;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import org.springframework.test.util.ReflectionTestUtils;

class AuthControllerRateLimitTest {
    @Test
    void loginAccountBucketBlocksRepeatedAttemptsIndependentlyOfIpFilter() {
        AuthService authService = mock(AuthService.class);
        ApiRateLimitService rateLimitService = new ApiRateLimitService();
        AuthController controller = new AuthController(authService, rateLimitService, mock(LoginCaptchaService.class));
        AuthController.LoginRequest request = new AuthController.LoginRequest("13800000001", "wrong-password");
        when(authService.login(request.phone(), request.password(), null, null, AuthService.SessionMetadata.unknown()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "手机号或密码错误"));

        for (int attempt = 0; attempt < 10; attempt++) {
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> controller.login(request));
            assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        }

        ResponseStatusException blocked = assertThrows(ResponseStatusException.class,
                () -> controller.login(request));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, blocked.getStatusCode());
        verify(authService, times(10)).login(request.phone(), request.password(), null, null,
                AuthService.SessionMetadata.unknown());
    }

    @Test
    void passwordChangeLimitsCurrentPasswordGuessesPerAccount() {
        AuthService authService = mock(AuthService.class);
        AppUser user = new AppUser("13800000002", "hash", "用户", UserRole.CUSTOMER);
        ReflectionTestUtils.setField(user, "id", 2L);
        when(authService.requireUser("Bearer token")).thenReturn(user);
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前密码错误"))
                .when(authService).changeOwnPassword("Bearer token", "wrong", "new-password");
        AuthController controller = new AuthController(authService, new ApiRateLimitService(),
                mock(LoginCaptchaService.class));
        var request = new AuthController.ChangePasswordRequest("wrong", "new-password");

        for (int attempt = 0; attempt < 5; attempt++) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                    () -> controller.changePassword("Bearer token", request)).getStatusCode());
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, assertThrows(ResponseStatusException.class,
                () -> controller.changePassword("Bearer token", request)).getStatusCode());
        verify(authService, times(5)).changeOwnPassword("Bearer token", "wrong", "new-password");
    }
}
