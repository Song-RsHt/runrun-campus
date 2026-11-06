package com.zhangxuan.runruncampus.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.enums.AdminRole;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.security.ApiRateLimitService;
import com.zhangxuan.runruncampus.service.AuthService;
import com.zhangxuan.runruncampus.service.LoginCaptchaService;

import org.junit.jupiter.api.Test;

class AuthControllerTest {

    @Test
    void currentUserReturnsLiveAdminRoleAndPermissions() {
        AuthService auth = mock(AuthService.class);
        AppUser admin = new AppUser("13900000000", "hash", "审核员", UserRole.ADMIN);
        admin.changeAdminRole(AdminRole.REVIEWER);
        when(auth.requireUser("Bearer token")).thenReturn(admin);
        AuthController controller =
                new AuthController(
                        auth, mock(ApiRateLimitService.class), mock(LoginCaptchaService.class));
        var user = controller.me("Bearer token");
        assertEquals(AdminRole.REVIEWER, user.adminRole());
        assertEquals(AdminRole.REVIEWER.getPermissions(), user.permissions());
    }
}
