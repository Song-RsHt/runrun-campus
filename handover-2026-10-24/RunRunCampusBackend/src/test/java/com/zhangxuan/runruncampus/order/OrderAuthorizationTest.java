package com.zhangxuan.runruncampus.order;

import com.zhangxuan.runruncampus.coupon.CouponService;
import com.zhangxuan.runruncampus.membership.MembershipService;
import com.zhangxuan.runruncampus.payment.PaymentService;
import com.zhangxuan.runruncampus.message.OrderMessageService;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderAuthorizationTest {
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderEventRepository eventRepository;
    @Mock
    private OrderProofRepository proofRepository;
    @Mock
    private OrderMessageService messageService;
    @Mock
    private CouponService couponService;
    @Mock
    private MembershipService membershipService;
    @Mock
    private PaymentService paymentService;
    @Mock
    private com.zhangxuan.runruncampus.courier.CourierCertificationService certificationService;
    @Mock
    private OrderProofStorage proofStorage;

    private OrderService orderService;
    private CampusOrder order;
    private AppUser customer;
    private AppUser courier;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, eventRepository, proofRepository, messageService,
                couponService, membershipService, org.mockito.Mockito.mock(com.zhangxuan.runruncampus.user.UserRepository.class),
                new com.fasterxml.jackson.databind.ObjectMapper(), paymentService,
                org.mockito.Mockito.mock(com.zhangxuan.runruncampus.api.PlatformSettingsService.class),
                certificationService,
                new com.zhangxuan.runruncampus.payment.ManualQrPaymentProperties(false));
        org.mockito.Mockito.lenient().when(certificationService.isEligible(org.mockito.ArgumentMatchers.any(AppUser.class)))
                .thenAnswer(call -> ((AppUser) call.getArgument(0)).isCourierApproved());
        customer = user(1L, UserRole.CUSTOMER);
        courier = user(2L, UserRole.COURIER);
        order = new CampusOrder("RP-AUTH-001", customer, "测试商家", "123456", "东门", "3号楼",
                ServiceType.LOBBY);
        ReflectionTestUtils.setField(order, "id", 11L);
    }

    @Test
    void availableOrderHidesPickupCodeUntilCourierAcceptsIt() {
        when(orderRepository.findFulfillablePendingOrders())
                .thenReturn(List.of(order));

        OrderController.OrderResponse response = orderService.available(courier).get(0);

        assertNull(response.pickupCode());
    }

    @Test
    void unrelatedUserCannotReadOrderDetail() {
        when(orderRepository.findById(11L)).thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> orderService.get(user(3L, UserRole.CUSTOMER), 11L));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void unrelatedUserCannotReadOrderEvents() {
        when(orderRepository.findById(11L)).thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> orderService.events(user(3L, UserRole.CUSTOMER), 11L));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(eventRepository);
    }

    @Test
    void unrelatedUserCannotReadCourierLocation() {
        order.assignCourier(courier);
        when(orderRepository.findById(11L)).thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> orderService.location(user(3L, UserRole.CUSTOMER), 11L));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void anotherCourierCannotUpdateOrderLocation() {
        order.assignCourier(courier);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> orderService.updateLocation(user(3L, UserRole.COURIER), 11L,
                        new OrderController.LocationUpdateRequest(31.49, 120.31)));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void anotherCustomerCannotReadDeliveryCode() {
        order.assignCourier(courier);
        order.updateStatus(OrderStatus.PICKED_UP);
        when(orderRepository.findById(11L)).thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> orderService.deliveryCode(user(3L, UserRole.CUSTOMER), 11L));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void anotherCustomerCannotApprovePackageSizeChange() {
        CampusOrder packageOrder = new CampusOrder("RP-AUTH-002", customer, "快递驿站", "654321",
                "校内驿站", "3号楼", ServiceType.PACKAGE_SMALL);
        ReflectionTestUtils.setField(packageOrder, "id", 12L);
        packageOrder.assignCourier(courier);
        packageOrder.requestPackageSizeChange(ServiceType.PACKAGE_LARGE);
        when(orderRepository.findLockedById(12L)).thenReturn(Optional.of(packageOrder));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> orderService.approvePackageSize(user(3L, UserRole.CUSTOMER), 12L));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals(ServiceType.PACKAGE_SMALL, packageOrder.getServiceType());
    }

    @Test
    void anotherCourierCannotUploadOrderProof() {
        order.assignCourier(courier);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));
        OrderProofService proofService = new OrderProofService(orderRepository, proofRepository, proofStorage);
        MockMultipartFile image = new MockMultipartFile(
                "file", "proof.jpg", "image/jpeg", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> proofService.upload(user(3L, UserRole.COURIER), 11L, OrderProofType.PICKUP, image));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(proofRepository, proofStorage);
    }

    private AppUser user(Long id, UserRole role) {
        AppUser user = new AppUser("1380000000" + id, "password-hash", "测试账号" + id, role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
