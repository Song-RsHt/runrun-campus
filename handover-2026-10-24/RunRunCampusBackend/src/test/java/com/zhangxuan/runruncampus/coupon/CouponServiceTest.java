package com.zhangxuan.runruncampus.coupon;

import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {
    @Mock
    private UserCouponRepository couponRepository;

    private CouponService couponService;
    private AppUser customer;

    @BeforeEach
    void setUp() {
        couponService = new CouponService(couponRepository);
        customer = user(1L);
    }

    @Test
    void firstListIssuesWelcomeCoupon() {
        when(couponRepository.existsByUser(customer)).thenReturn(false);

        couponService.list(customer);

        verify(couponRepository).save(any(UserCoupon.class));
    }

    @Test
    void ownerCanApplyAvailableCouponOnce() {
        UserCoupon coupon = coupon(51L, customer, Instant.now().plusSeconds(3600));
        when(couponRepository.findLockedById(51L)).thenReturn(Optional.of(coupon));

        CouponService.CouponApplication application = couponService.apply(customer, 51L, 200);

        assertEquals(100, application.discountCents());
        assertEquals("新用户立减券", application.couponTitle());
        assertEquals(51L, application.couponId());
        assertTrue(coupon.isUsed());
    }

    @Test
    void cancellingOrderRestoresItsCoupon() {
        UserCoupon coupon = coupon(51L, customer, Instant.now().plusSeconds(3600));
        coupon.markUsed();
        when(couponRepository.findLockedById(51L)).thenReturn(Optional.of(coupon));

        couponService.refund(customer, 51L);

        assertFalse(coupon.isUsed());
    }

    @Test
    void anotherUserCannotApplyCoupon() {
        UserCoupon coupon = coupon(51L, customer, Instant.now().plusSeconds(3600));
        when(couponRepository.findLockedById(51L)).thenReturn(Optional.of(coupon));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> couponService.apply(user(2L), 51L, 200));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void expiredCouponCannotBeApplied() {
        UserCoupon coupon = coupon(51L, customer, Instant.now().minusSeconds(1));
        when(couponRepository.findLockedById(51L)).thenReturn(Optional.of(coupon));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> couponService.apply(customer, 51L, 200));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void couponMinimumMustBeMet() {
        UserCoupon coupon = coupon(51L, customer, Instant.now().plusSeconds(3600));
        when(couponRepository.findLockedById(51L)).thenReturn(Optional.of(coupon));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> couponService.apply(customer, 51L, 199));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void usedCouponCannotBeAppliedAgain() {
        UserCoupon coupon = coupon(51L, customer, Instant.now().plusSeconds(3600));
        coupon.markUsed();
        when(couponRepository.findLockedById(51L)).thenReturn(Optional.of(coupon));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> couponService.apply(customer, 51L, 200));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void courierCannotListCoupons() {
        AppUser courier = new AppUser("13800000009", "password-hash", "测试骑手", UserRole.COURIER);
        ReflectionTestUtils.setField(courier, "id", 9L);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> couponService.list(courier));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(couponRepository);
    }

    private UserCoupon coupon(Long id, AppUser owner, Instant expiresAt) {
        UserCoupon coupon = new UserCoupon(owner, "新用户立减券", 100, 200, expiresAt);
        ReflectionTestUtils.setField(coupon, "id", id);
        return coupon;
    }

    private AppUser user(Long id) {
        AppUser user = new AppUser("1380000000" + id, "password-hash", "测试用户" + id, UserRole.CUSTOMER);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
