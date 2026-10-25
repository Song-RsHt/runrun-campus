package com.zhangxuan.runruncampus.auth;

import com.zhangxuan.runruncampus.admin.AdminRole;
import com.zhangxuan.runruncampus.api.ApiRateLimitService;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {
    @Test
    void currentUserReturnsLiveAdminRoleAndPermissions() {
        AuthService auth = mock(AuthService.class);
        AppUser admin = new AppUser("13900000000", "hash", "审核员", UserRole.ADMIN);
        admin.changeAdminRole(AdminRole.REVIEWER);
        when(auth.requireUser("Bearer token")).thenReturn(admin);
        AuthController controller = new AuthController(auth, mock(ApiRateLimitService.class),
                mock(LoginCaptchaService.class));

        var user = controller.me("Bearer token");

        assertEquals(AdminRole.REVIEWER, user.adminRole());
        assertEquals(AdminRole.REVIEWER.getPermissions(), user.permissions());
    }
}
