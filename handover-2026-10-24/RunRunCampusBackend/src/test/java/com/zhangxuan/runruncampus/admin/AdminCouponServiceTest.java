package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.coupon.UserCoupon;
import com.zhangxuan.runruncampus.coupon.UserCouponRepository;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRepository;
import com.zhangxuan.runruncampus.user.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminCouponServiceTest {
    @Mock UserRepository userRepository;
    @Mock UserCouponRepository couponRepository;
    @Mock AdminAuditService auditService;

    @Test
    void operationsAdministratorIssuesFixedOneYuanCouponAndWritesAudit() {
        AdminCouponService service = new AdminCouponService(userRepository, couponRepository, auditService);
        AppUser administrator = user(1L, "运营管理员", UserRole.ADMIN);
        AppUser customer = user(2L, "测试用户", UserRole.CUSTOMER);
        when(userRepository.findLockedById(2L)).thenReturn(Optional.of(customer));
        when(couponRepository.save(any(UserCoupon.class))).thenAnswer(invocation -> {
            UserCoupon coupon = invocation.getArgument(0);
            ReflectionTestUtils.setField(coupon, "id", 51L);
            return coupon;
        });
        Instant before = Instant.now().plusSeconds(29L * 24 * 3600);

        AdminCouponService.GrantResponse result = service.issueOneYuan(administrator, 2L);

        assertEquals(51L, result.couponId());
        assertEquals(100, result.discountCents());
        assertEquals(200, result.minimumOrderCents());
        assertEquals("管理员赠送1元券", result.title());
        assertTrue(result.expiresAt().isAfter(before));
        verify(auditService).record(administrator, AdminAuditAction.COUPON_ISSUED,
                AdminAuditTargetType.USER, 2L, "向用户发放1元优惠券：测试用户");
    }

    @Test
    void couponCannotBeIssuedToCourier() {
        AdminCouponService service = new AdminCouponService(userRepository, couponRepository, auditService);
        AppUser courier = user(2L, "测试骑手", UserRole.COURIER);
        when(userRepository.findLockedById(2L)).thenReturn(Optional.of(courier));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.issueOneYuan(user(1L, "管理员", UserRole.ADMIN), 2L));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verifyNoInteractions(couponRepository, auditService);
    }

    @Test
    void onlySuperAdministratorAndOperationsRoleHaveCouponPermission() {
        assertTrue(AdminRole.SUPER_ADMIN.permits(AdminPermission.COUPON_ISSUE));
        assertTrue(AdminRole.OPERATIONS.permits(AdminPermission.COUPON_ISSUE));
        assertTrue(!AdminRole.SUPPORT.permits(AdminPermission.COUPON_ISSUE));
        assertTrue(!AdminRole.REVIEWER.permits(AdminPermission.COUPON_ISSUE));
        assertTrue(!AdminRole.AUDITOR.permits(AdminPermission.COUPON_ISSUE));
    }

    private AppUser user(long id, String name, UserRole role) {
        AppUser user = new AppUser("1380000000" + id, "hash", name, role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
