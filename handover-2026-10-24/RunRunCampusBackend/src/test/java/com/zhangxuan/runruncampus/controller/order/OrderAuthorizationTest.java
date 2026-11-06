package com.zhangxuan.runruncampus.controller.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhangxuan.runruncampus.config.ManualQrPaymentProperties;
import com.zhangxuan.runruncampus.dto.LocationUpdateRequest;
import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.CampusOrder;
import com.zhangxuan.runruncampus.enums.OrderProofType;
import com.zhangxuan.runruncampus.enums.OrderStatus;
import com.zhangxuan.runruncampus.enums.ServiceType;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.exception.BusinessFailure;
import com.zhangxuan.runruncampus.integration.storage.OrderProofStorage;
import com.zhangxuan.runruncampus.integration.web.MultipartUploads;
import com.zhangxuan.runruncampus.repository.OrderEventRepository;
import com.zhangxuan.runruncampus.repository.OrderProofRepository;
import com.zhangxuan.runruncampus.repository.OrderRepository;
import com.zhangxuan.runruncampus.repository.UserRepository;
import com.zhangxuan.runruncampus.service.CouponService;
import com.zhangxuan.runruncampus.service.CourierCertificationService;
import com.zhangxuan.runruncampus.service.MembershipService;
import com.zhangxuan.runruncampus.service.OrderMessageService;
import com.zhangxuan.runruncampus.service.OrderProofService;
import com.zhangxuan.runruncampus.service.OrderService;
import com.zhangxuan.runruncampus.service.PaymentService;
import com.zhangxuan.runruncampus.service.PlatformSettingsService;
import com.zhangxuan.runruncampus.service.impl.OrderProofServiceImpl;
import com.zhangxuan.runruncampus.service.impl.OrderServiceImpl;
import com.zhangxuan.runruncampus.vo.OrderResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class OrderAuthorizationTest {

    @Mock private OrderRepository orderRepository;

    @Mock private OrderEventRepository eventRepository;

    @Mock private OrderProofRepository proofRepository;

    @Mock private OrderMessageService messageService;

    @Mock private CouponService couponService;

    @Mock private MembershipService membershipService;

    @Mock private PaymentService paymentService;

    @Mock private CourierCertificationService certificationService;

    @Mock private OrderProofStorage proofStorage;

    private OrderService orderService;

    private CampusOrder order;

    private AppUser customer;

    private AppUser courier;

    @BeforeEach
    void setUp() {
        orderService =
                new OrderServiceImpl(
                        orderRepository,
                        eventRepository,
                        proofRepository,
                        messageService,
                        couponService,
                        membershipService,
                        Mockito.mock(UserRepository.class),
                        new ObjectMapper(),
                        paymentService,
                        Mockito.mock(PlatformSettingsService.class),
                        certificationService,
                        new ManualQrPaymentProperties(false));
        Mockito.lenient()
                .when(certificationService.isEligible(ArgumentMatchers.any(AppUser.class)))
                .thenAnswer(call -> ((AppUser) call.getArgument(0)).isCourierApproved());
        customer = user(1L, UserRole.CUSTOMER);
        courier = user(2L, UserRole.COURIER);
        order =
                new CampusOrder(
                        "RP-AUTH-001", customer, "测试商家", "123456", "东门", "3号楼", ServiceType.LOBBY);
        ReflectionTestUtils.setField(order, "id", 11L);
    }

    @Test
    void availableOrderHidesPickupCodeUntilCourierAcceptsIt() {
        when(orderRepository.findFulfillablePendingOrders()).thenReturn(List.of(order));
        OrderResponse response = orderService.available(courier).get(0);
        assertNull(response.pickupCode());
    }

    @Test
    void unrelatedUserCannotReadOrderDetail() {
        when(orderRepository.findById(11L)).thenReturn(Optional.of(order));
        BusinessFailure exception =
                assertThrows(
                        BusinessFailure.class,
                        () -> orderService.get(user(3L, UserRole.CUSTOMER), 11L));
        assertEquals(HttpStatus.FORBIDDEN.value(), exception.status());
    }

    @Test
    void unrelatedUserCannotReadOrderEvents() {
        when(orderRepository.findById(11L)).thenReturn(Optional.of(order));
        BusinessFailure exception =
                assertThrows(
                        BusinessFailure.class,
                        () -> orderService.events(user(3L, UserRole.CUSTOMER), 11L));
        assertEquals(HttpStatus.FORBIDDEN.value(), exception.status());
        verifyNoInteractions(eventRepository);
    }

    @Test
    void unrelatedUserCannotReadCourierLocation() {
        order.assignCourier(courier);
        when(orderRepository.findById(11L)).thenReturn(Optional.of(order));
        BusinessFailure exception =
                assertThrows(
                        BusinessFailure.class,
                        () -> orderService.location(user(3L, UserRole.CUSTOMER), 11L));
        assertEquals(HttpStatus.FORBIDDEN.value(), exception.status());
    }

    @Test
    void anotherCourierCannotUpdateOrderLocation() {
        order.assignCourier(courier);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));
        BusinessFailure exception =
                assertThrows(
                        BusinessFailure.class,
                        () ->
                                orderService.updateLocation(
                                        user(3L, UserRole.COURIER),
                                        11L,
                                        new LocationUpdateRequest(31.49, 120.31)));
        assertEquals(HttpStatus.FORBIDDEN.value(), exception.status());
    }

    @Test
    void anotherCustomerCannotReadDeliveryCode() {
        order.assignCourier(courier);
        order.updateStatus(OrderStatus.PICKED_UP);
        when(orderRepository.findById(11L)).thenReturn(Optional.of(order));
        BusinessFailure exception =
                assertThrows(
                        BusinessFailure.class,
                        () -> orderService.deliveryCode(user(3L, UserRole.CUSTOMER), 11L));
        assertEquals(HttpStatus.FORBIDDEN.value(), exception.status());
    }

    @Test
    void anotherCustomerCannotApprovePackageSizeChange() {
        CampusOrder packageOrder =
                new CampusOrder(
                        "RP-AUTH-002",
                        customer,
                        "快递驿站",
                        "654321",
                        "校内驿站",
                        "3号楼",
                        ServiceType.PACKAGE_SMALL);
        ReflectionTestUtils.setField(packageOrder, "id", 12L);
        packageOrder.assignCourier(courier);
        packageOrder.requestPackageSizeChange(ServiceType.PACKAGE_LARGE);
        when(orderRepository.findLockedById(12L)).thenReturn(Optional.of(packageOrder));
        BusinessFailure exception =
                assertThrows(
                        BusinessFailure.class,
                        () -> orderService.approvePackageSize(user(3L, UserRole.CUSTOMER), 12L));
        assertEquals(HttpStatus.FORBIDDEN.value(), exception.status());
        assertEquals(ServiceType.PACKAGE_SMALL, packageOrder.getServiceType());
    }

    @Test
    void anotherCourierCannotUploadOrderProof() {
        order.assignCourier(courier);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));
        OrderProofService proofService =
                new OrderProofServiceImpl(orderRepository, proofRepository, proofStorage);
        MockMultipartFile image =
                new MockMultipartFile(
                        "file",
                        "proof.jpg",
                        "image/jpeg",
                        new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});
        BusinessFailure exception =
                assertThrows(
                        BusinessFailure.class,
                        () ->
                                proofService.upload(
                                        user(3L, UserRole.COURIER),
                                        11L,
                                        OrderProofType.PICKUP,
                                        MultipartUploads.adapt(image)));
        assertEquals(HttpStatus.FORBIDDEN.value(), exception.status());
        verifyNoInteractions(proofRepository, proofStorage);
    }

    private AppUser user(Long id, UserRole role) {
        AppUser user = new AppUser("1380000000" + id, "password-hash", "测试账号" + id, role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
