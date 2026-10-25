package com.zhangxuan.runruncampus.courier;

import com.zhangxuan.runruncampus.order.OrderRepository;
import com.zhangxuan.runruncampus.order.OrderStatus;
import com.zhangxuan.runruncampus.api.PlatformSettingsService;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRepository;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourierOperationsServiceTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderRepository orderRepository;
    @Mock private CourierCertificationService certificationService;
    @Mock private PlatformSettingsService settingsService;

    private CourierOperationsService service;
    private AppUser courier;

    @BeforeEach
    void setUp() {
        service = new CourierOperationsService(userRepository, orderRepository, certificationService, settingsService);
        org.mockito.Mockito.lenient().when(certificationService.isEligible(any(AppUser.class)))
                .thenAnswer(call -> ((AppUser) call.getArgument(0)).isCourierApproved());
        org.mockito.Mockito.lenient().when(settingsService.get())
                .thenReturn(new PlatformSettingsService.Settings("09:00–23:00", 10, 30, 2, 30, 120));
        courier = new AppUser("13800000003", "hash", "骑手", UserRole.COURIER);
        ReflectionTestUtils.setField(courier, "id", 1L);
        courier.approveCourier();
        courier.setCourierAcceptingOrders(false);
    }

    @Test
    void approvedCourierCanGoOnlineAndReadBusinessMetrics() {
        OrderRepository.CourierBusinessStats stats = mock(OrderRepository.CourierBusinessStats.class);
        when(stats.getTodayCompletedOrders()).thenReturn(3L);
        when(stats.getTotalCompletedOrders()).thenReturn(21L);
        when(stats.getTodayGrossServiceFeeCents()).thenReturn(900L);
        when(stats.getTotalGrossServiceFeeCents()).thenReturn(6300L);
        when(userRepository.findLockedById(1L)).thenReturn(Optional.of(courier));
        when(orderRepository.businessStatsForCourier(eq(courier), any(Instant.class))).thenReturn(stats);
        when(orderRepository.averageRatingForCourier(courier)).thenReturn(4.6);
        when(orderRepository.countFulfillablePendingOrders()).thenReturn(5L);
        when(orderRepository.countByCourierAndStatusIn(courier,
                List.of(OrderStatus.ACCEPTED, OrderStatus.PICKED_UP))).thenReturn(1L);
        when(orderRepository.countByCourierAndRatingScoreIsNotNull(courier)).thenReturn(18L);

        CourierOperationsController.OperationsResponse response = service.updateAvailability(courier, true);

        assertTrue(response.acceptingOrders());
        assertEquals(5L, response.availableOrders());
        assertEquals(1L, response.activeOrders());
        assertEquals(3L, response.todayCompletedOrders());
        assertEquals(21L, response.totalCompletedOrders());
        assertEquals(900L, response.todayGrossServiceFeeCents());
        assertEquals(6300L, response.totalGrossServiceFeeCents());
        assertEquals(4.6, response.averageRating());
        assertEquals(18L, response.ratingCount());
        assertNotNull(response.availabilityUpdatedAt());
        verify(userRepository).findLockedById(1L);
    }

    @Test
    void unapprovedCourierCannotGoOnline() {
        courier.revokeCourierApproval();
        when(userRepository.findLockedById(1L)).thenReturn(Optional.of(courier));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.updateAvailability(courier, true));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals("骑手认证有效后才能上线接单", exception.getReason());
    }
}
