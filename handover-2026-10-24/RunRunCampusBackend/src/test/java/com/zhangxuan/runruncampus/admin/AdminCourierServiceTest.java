package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.courier.CourierCertificationRepository;
import com.zhangxuan.runruncampus.order.CampusOrder;
import com.zhangxuan.runruncampus.order.OrderRepository;
import com.zhangxuan.runruncampus.order.OrderStatus;
import com.zhangxuan.runruncampus.order.ServiceType;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRepository;
import com.zhangxuan.runruncampus.user.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminCourierServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private CourierCertificationRepository certificationRepository;
    @Mock
    private OrderRepository orderRepository;

    @Test
    void returnsPackagePerformanceAndPagedOrderDetails() {
        AppUser courier = mock(AppUser.class);
        AppUser customer = mock(AppUser.class);
        CampusOrder order = mock(CampusOrder.class);
        OrderRepository.CourierPackagePerformanceStats stats =
                mock(OrderRepository.CourierPackagePerformanceStats.class);
        Instant createdAt = Instant.parse("2026-09-01T01:00:00Z");

        when(courier.getId()).thenReturn(9L);
        when(courier.getRole()).thenReturn(UserRole.COURIER);
        when(customer.getDisplayName()).thenReturn("测试用户");
        when(order.getId()).thenReturn(81L);
        when(order.getOrderNo()).thenReturn("PKG-81");
        when(order.getStatus()).thenReturn(OrderStatus.COMPLETED);
        when(order.getServiceType()).thenReturn(ServiceType.PACKAGE_MEDIUM);
        when(order.getDestination()).thenReturn("3号楼");
        when(order.getFeeCents()).thenReturn(200);
        when(order.getCustomer()).thenReturn(customer);
        when(order.getCreatedAt()).thenReturn(createdAt);
        when(order.getUpdatedAt()).thenReturn(createdAt);
        when(stats.getTodayCompletedPackageOrders()).thenReturn(2L);
        when(stats.getLastSevenDaysCompletedPackageOrders()).thenReturn(5L);
        when(stats.getCurrentMonthCompletedPackageOrders()).thenReturn(8L);
        when(stats.getTotalCompletedPackageOrders()).thenReturn(20L);
        when(stats.getSmallCompletedPackageOrders()).thenReturn(10L);
        when(stats.getMediumCompletedPackageOrders()).thenReturn(6L);
        when(stats.getLargeCompletedPackageOrders()).thenReturn(4L);
        when(stats.getExtraLargeCompletedPackageOrders()).thenReturn(2L);
        when(userRepository.findById(9L)).thenReturn(Optional.of(courier));
        when(orderRepository.packagePerformanceForCourier(eq(9L), any(), any(), any())).thenReturn(stats);
        when(orderRepository.findPackageOrdersForCourier(eq(9L), any())).thenReturn(
                new PageImpl<>(List.of(order), PageRequest.of(0, 10), 1));

        AdminCourierController.AdminCourierPerformanceResponse response = service().performance(9L, 0, 10);

        assertEquals(2, response.todayCompletedPackageOrders());
        assertEquals(5, response.lastSevenDaysCompletedPackageOrders());
        assertEquals(8, response.currentMonthCompletedPackageOrders());
        assertEquals(20, response.totalCompletedPackageOrders());
        assertEquals(10, response.smallCompletedPackageOrders());
        assertEquals(6, response.mediumCompletedPackageOrders());
        assertEquals(4, response.largeCompletedPackageOrders());
        assertEquals(2, response.extraLargeCompletedPackageOrders());
        assertEquals(1, response.orders().totalElements());
        assertEquals("PKG-81", response.orders().items().get(0).orderNo());
        assertEquals("测试用户", response.orders().items().get(0).customerName());
    }

    private AdminCourierService service() {
        return new AdminCourierService(userRepository, certificationRepository, orderRepository);
    }
}
