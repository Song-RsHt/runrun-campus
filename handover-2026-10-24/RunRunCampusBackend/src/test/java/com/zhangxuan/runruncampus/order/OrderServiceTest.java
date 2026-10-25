package com.zhangxuan.runruncampus.order;

import com.zhangxuan.runruncampus.message.OrderMessageService;
import com.zhangxuan.runruncampus.message.OrderMessageType;
import com.zhangxuan.runruncampus.coupon.CouponService;
import com.zhangxuan.runruncampus.membership.MembershipService;
import com.zhangxuan.runruncampus.payment.PaymentService;
import com.zhangxuan.runruncampus.api.PlatformSettingsService;
import com.zhangxuan.runruncampus.courier.CourierCertificationService;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
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
    private PlatformSettingsService settingsService;

    @Mock
    private CourierCertificationService certificationService;

    @Mock
    private com.zhangxuan.runruncampus.user.UserRepository userRepository;

    private OrderService orderService;
    private AppUser customer;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, eventRepository, proofRepository, messageService,
                couponService, membershipService, userRepository,
                new com.fasterxml.jackson.databind.ObjectMapper(), paymentService, settingsService, certificationService,
                new com.zhangxuan.runruncampus.payment.ManualQrPaymentProperties(true));
        org.mockito.Mockito.lenient().when(certificationService.isEligible(any(AppUser.class)))
                .thenAnswer(call -> ((AppUser) call.getArgument(0)).isCourierApproved());
        customer = user(1L, "13800000001", UserRole.CUSTOMER);
    }

    @Test
    void customerCanCancelOwnPendingOrder() {
        CampusOrder order = order(customer);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));

        OrderController.OrderResponse response = orderService.cancel(customer, 11L);

        assertEquals(OrderStatus.CANCELLED, response.status());
        verify(eventRepository).save(argThat(event ->
                event.getActor() == customer && event.getStatus() == OrderStatus.CANCELLED));
    }

    @Test
    void historyIsPagedAndScopedToCurrentCustomer() {
        CampusOrder completed = order(customer);
        completed.updateStatus(OrderStatus.COMPLETED);
        when(orderRepository.findByCustomerAndStatusIn(eq(customer),
                eq(List.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED)), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(completed), PageRequest.of(1, 20), 41));

        var result = orderService.mineHistory(customer, 1, 20);

        assertEquals(1, result.page());
        assertEquals(41, result.totalElements());
        assertEquals(3, result.totalPages());
        assertEquals(OrderStatus.COMPLETED, result.items().get(0).status());
    }

    @Test
    void historyCanBeFilteredByStatusAndInclusiveShanghaiDates() {
        CampusOrder completed = order(customer);
        completed.updateStatus(OrderStatus.COMPLETED);
        Instant start = Instant.parse("2026-08-31T16:00:00Z");
        Instant endExclusive = Instant.parse("2026-09-03T16:00:00Z");
        when(orderRepository.findHistoryByCustomer(eq(customer), eq(List.of(OrderStatus.COMPLETED)),
                eq(true), eq(true), eq(start), eq(endExclusive), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(completed)));

        var result = orderService.mineHistory(customer, 0, 20, OrderStatus.COMPLETED,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3));

        assertEquals(1, result.totalElements());
        verify(orderRepository).findHistoryByCustomer(eq(customer), eq(List.of(OrderStatus.COMPLETED)),
                eq(true), eq(true), eq(start), eq(endExclusive), any(Pageable.class));
    }

    @Test
    void historyRejectsActiveStatusAndReversedDates() {
        ResponseStatusException activeStatus = assertThrows(ResponseStatusException.class,
                () -> orderService.mineHistory(customer, 0, 20, OrderStatus.ACCEPTED, null, null));
        ResponseStatusException reversedDates = assertThrows(ResponseStatusException.class,
                () -> orderService.mineHistory(customer, 0, 20, null,
                        LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 1)));

        assertEquals(HttpStatus.BAD_REQUEST, activeStatus.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, reversedDates.getStatusCode());
        verifyNoInteractions(orderRepository);
    }

    @Test
    void activeOrdersExcludeCompletedHistory() {
        CampusOrder active = order(customer);
        when(orderRepository.findByCustomerAndStatusInOrderByCreatedAtDesc(customer,
                List.of(OrderStatus.PENDING_ASSIGNMENT, OrderStatus.PENDING_REASSIGNMENT,
                        OrderStatus.ACCEPTED, OrderStatus.PICKED_UP)))
                .thenReturn(List.of(active));

        var result = orderService.mineActive(customer);

        assertEquals(1, result.size());
        assertEquals(OrderStatus.PENDING_ASSIGNMENT, result.get(0).status());
    }

    @Test
    void courierHistoryUsesCourierOwnershipInsteadOfCustomerOwnership() {
        AppUser courier = user(2L, "13800000002", UserRole.COURIER);
        CampusOrder completed = order(customer);
        completed.assignCourier(courier);
        completed.updateStatus(OrderStatus.COMPLETED);
        when(orderRepository.findByCourierAndStatusIn(eq(courier),
                eq(List.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED)), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(completed), PageRequest.of(0, 20), 1));

        var result = orderService.mineHistory(courier, 0, 20);

        assertEquals(1, result.totalElements());
        assertEquals(OrderStatus.COMPLETED, result.items().get(0).status());
    }

    @Test
    void historyRejectsOversizedPage() {
        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> orderService.mineHistory(customer, 0, 51));

        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
        verifyNoInteractions(orderRepository);
    }

    @Test
    void courierAtConfiguredCapacityCannotAcceptAnotherOrder() {
        AppUser courier = user(2L, "13800000002", UserRole.COURIER);
        courier.setCourierAcceptingOrders(true);
        CampusOrder order = order(customer);
        when(userRepository.findLockedById(2L)).thenReturn(Optional.of(courier));
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));
        when(orderRepository.countByCourierAndStatusIn(courier,
                java.util.List.of(OrderStatus.ACCEPTED, OrderStatus.PICKED_UP))).thenReturn(2L);
        when(settingsService.get()).thenReturn(new PlatformSettingsService.Settings("09:00–23:00", 10, 30, 2, 30, 120));

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> orderService.accept(courier, 11L));

        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        assertEquals(OrderStatus.PENDING_ASSIGNMENT, order.getStatus());
        verifyNoInteractions(eventRepository, messageService);
    }

    @Test
    void tenOrderLimitAllowsNinthActiveOrderToAcceptTenth() {
        AppUser courier = user(2L, "13800000002", UserRole.COURIER);
        CampusOrder order = order(customer);
        when(userRepository.findLockedById(2L)).thenReturn(Optional.of(courier));
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));
        when(orderRepository.countByCourierAndStatusIn(eq(courier), any())).thenReturn(9L);
        when(settingsService.get()).thenReturn(new PlatformSettingsService.Settings("09:00–23:00", 10, 30, 10, 30, 120));

        orderService.accept(courier, 11L);

        assertEquals(OrderStatus.ACCEPTED, order.getStatus());
        assertEquals(courier, order.getCourier());
    }

    @Test
    void tenActiveOrdersCannotAcceptEleventh() {
        AppUser courier = user(2L, "13800000002", UserRole.COURIER);
        CampusOrder order = order(customer);
        when(userRepository.findLockedById(2L)).thenReturn(Optional.of(courier));
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));
        when(orderRepository.countByCourierAndStatusIn(eq(courier), any())).thenReturn(10L);
        when(settingsService.get()).thenReturn(new PlatformSettingsService.Settings("09:00–23:00", 10, 30, 10, 30, 120));

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> orderService.accept(courier, 11L));

        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        assertEquals(OrderStatus.PENDING_ASSIGNMENT, order.getStatus());
    }

    @Test
    void courierTakenOfflineBeforeRowLockCannotAccept() {
        AppUser staleCourier = user(2L, "13800000002", UserRole.COURIER);
        staleCourier.setCourierAcceptingOrders(true);
        AppUser lockedCourier = user(2L, "13800000002", UserRole.COURIER);
        lockedCourier.setCourierAcceptingOrders(false);
        CampusOrder order = order(customer);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));
        when(userRepository.findLockedById(2L)).thenReturn(Optional.of(lockedCourier));

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> orderService.accept(staleCourier, 11L));

        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        assertEquals(OrderStatus.PENDING_ASSIGNMENT, order.getStatus());
        verifyNoInteractions(eventRepository, messageService);
    }

    @Test
    void expiredCertificationCannotAcceptOrderDespiteOldApprovalFlag() {
        AppUser courier = user(2L, "13800000002", UserRole.COURIER);
        when(certificationService.isEligible(courier)).thenReturn(false);

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> orderService.accept(courier, 11L));

        assertEquals(HttpStatus.FORBIDDEN, failure.getStatusCode());
        verifyNoInteractions(orderRepository);
    }

    @Test
    void cancellingMembershipOrderRestoresItsTrip() {
        CampusOrder order = new CampusOrder("RP-MEMBER-CANCEL", customer, "测试商家", "1234", "东门", "3号楼",
                ServiceType.LOBBY, 200, 200, null, true, "学生月卡", 41L);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));

        OrderController.OrderResponse response = orderService.cancel(customer, 11L);

        assertEquals(OrderStatus.CANCELLED, response.status());
        verify(membershipService).refund(customer, 41L);
    }

    @Test
    void cancellingCouponOrderRestoresItsCoupon() {
        CampusOrder order = new CampusOrder("RP-COUPON-CANCEL", customer, "测试商家", "1234", "东门", "3号楼",
                ServiceType.LOBBY, 200, 100, "新用户立减券");
        ReflectionTestUtils.setField(order, "couponId", 51L);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));

        OrderController.OrderResponse response = orderService.cancel(customer, 11L);

        assertEquals(OrderStatus.CANCELLED, response.status());
        verify(couponService).refund(customer, 51L);
    }

    @Test
    void anotherCustomerCannotCancelOrder() {
        CampusOrder order = order(customer);
        AppUser otherCustomer = user(2L, "13800000002", UserRole.CUSTOMER);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> orderService.cancel(otherCustomer, 11L));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals(OrderStatus.PENDING_ASSIGNMENT, order.getStatus());
        verifyNoInteractions(eventRepository);
    }

    @Test
    void acceptedOrderCannotBeCancelled() {
        CampusOrder order = order(customer);
        order.assignCourier(user(3L, "13800000003", UserRole.COURIER));
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> orderService.cancel(customer, 11L));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(OrderStatus.ACCEPTED, order.getStatus());
        verifyNoInteractions(eventRepository);
    }

    @Test
    void pickupRequiresPickupProof() {
        CampusOrder order = order(customer);
        AppUser courier = user(3L, "13800000003", UserRole.COURIER);
        order.assignCourier(courier);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> orderService.markPickedUp(courier, 11L));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(OrderStatus.ACCEPTED, order.getStatus());
    }

    @Test
    void packagePickupRequiresConfirmedSize() {
        CampusOrder order = packageOrder(customer, ServiceType.PACKAGE_SMALL);
        AppUser courier = user(3L, "13800000003", UserRole.COURIER);
        order.assignCourier(courier);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> orderService.markPickedUp(courier, 11L));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(OrderStatus.ACCEPTED, order.getStatus());
    }

    @Test
    void courierCanConfirmOriginalPackageSize() {
        CampusOrder order = packageOrder(customer, ServiceType.PACKAGE_SMALL);
        AppUser courier = user(3L, "13800000003", UserRole.COURIER);
        order.assignCourier(courier);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));

        OrderController.OrderResponse response = orderService.confirmPackageSize(
                courier, 11L, new OrderController.ConfirmPackageSizeRequest(ServiceType.PACKAGE_SMALL));

        assertTrue(response.packageSizeConfirmed());
        assertEquals(ServiceType.PACKAGE_SMALL, response.serviceType());
        assertEquals(100, response.feeCents());
    }

    @Test
    void packageSizeChangeWaitsForCustomerApprovalBeforeChangingFee() {
        CampusOrder order = packageOrder(customer, ServiceType.PACKAGE_SMALL);
        AppUser courier = user(3L, "13800000003", UserRole.COURIER);
        order.assignCourier(courier);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));
        when(proofRepository.existsByOrderAndType(order, OrderProofType.SIZE_REVIEW)).thenReturn(true);

        OrderController.OrderResponse requested = orderService.confirmPackageSize(
                courier, 11L, new OrderController.ConfirmPackageSizeRequest(ServiceType.PACKAGE_LARGE));

        assertEquals(ServiceType.PACKAGE_SMALL, requested.serviceType());
        assertEquals(ServiceType.PACKAGE_LARGE, requested.proposedPackageServiceType());
        assertEquals(100, requested.feeCents());
        assertFalse(requested.packageSizeConfirmed());
        verify(messageService).send(eq(customer), eq(order),
                eq(OrderMessageType.PACKAGE_SIZE_CHANGE_REQUESTED), anyString(), anyString());

        OrderController.OrderResponse approved = orderService.approvePackageSize(customer, 11L);

        assertEquals(ServiceType.PACKAGE_LARGE, approved.serviceType());
        assertEquals(400, approved.feeCents());
        assertTrue(approved.packageSizeConfirmed());
        assertNull(approved.proposedPackageServiceType());
        verify(messageService).send(eq(courier), eq(order),
                eq(OrderMessageType.PACKAGE_SIZE_CHANGE_APPROVED), anyString(), anyString());
    }

    @Test
    void membershipPackageCannotBeChangedToSmall() {
        CampusOrder order = new CampusOrder("RR-MEMBER-SIZE", customer, "菜鸟驿站", "1234",
                "校内快递驿站", "3号楼", ServiceType.PACKAGE_MEDIUM, 200, 200, null,
                true, "学生月卡", 41L);
        AppUser courier = user(3L, "13800000003", UserRole.COURIER);
        order.assignCourier(courier);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> orderService.confirmPackageSize(
                        courier, 11L, new OrderController.ConfirmPackageSizeRequest(ServiceType.PACKAGE_SMALL)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("月卡订单不能调整为小件，请用户取消后重新下单", exception.getReason());
        assertNull(order.getProposedPackageServiceType());
    }

    @Test
    void rejectingPackageSizeIncreaseCancelsOrderAndRestoresMembership() {
        CampusOrder order = new CampusOrder("RP-PACKAGE-REJECT", customer, "菜鸟驿站", "1234", "校内快递驿站",
                "3号楼", ServiceType.PACKAGE_SMALL, 100, 100, null, true, "学生月卡", 41L);
        AppUser courier = user(3L, "13800000003", UserRole.COURIER);
        order.assignCourier(courier);
        order.requestPackageSizeChange(ServiceType.PACKAGE_LARGE);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));

        OrderController.OrderResponse response = orderService.rejectPackageSize(customer, 11L);

        assertEquals(ServiceType.PACKAGE_SMALL, response.serviceType());
        assertEquals(OrderStatus.CANCELLED, response.status());
        assertNull(response.proposedPackageServiceType());
        assertFalse(response.packageSizeConfirmed());
        verify(membershipService).refund(customer, 41L);
        verify(eventRepository).save(argThat(event -> event.getStatus() == OrderStatus.CANCELLED));
        verify(messageService).send(eq(courier), eq(order),
                eq(OrderMessageType.PACKAGE_SIZE_CHANGE_REJECTED), anyString(), anyString());
    }

    @Test
    void everyPackageSizeIncreaseRequiresReviewProof() {
        CampusOrder order = packageOrder(customer, ServiceType.PACKAGE_SMALL);
        AppUser courier = user(3L, "13800000003", UserRole.COURIER);
        order.assignCourier(courier);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> orderService.confirmPackageSize(
                        courier, 11L, new OrderController.ConfirmPackageSizeRequest(ServiceType.PACKAGE_MEDIUM)));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("提高包裹规格前请先上传规格核验照片", exception.getReason());
        assertNull(order.getProposedPackageServiceType());
    }

    @Test
    void packageSizeDecreaseDoesNotRequireReviewProof() {
        CampusOrder order = packageOrder(customer, ServiceType.PACKAGE_LARGE);
        AppUser courier = user(3L, "13800000003", UserRole.COURIER);
        order.assignCourier(courier);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));

        OrderController.OrderResponse response = orderService.confirmPackageSize(
                courier, 11L, new OrderController.ConfirmPackageSizeRequest(ServiceType.PACKAGE_MEDIUM));

        assertEquals(ServiceType.PACKAGE_MEDIUM, response.proposedPackageServiceType());
    }

    @Test
    void deliveryCompletionRequiresDeliveryProof() {
        CampusOrder order = order(customer);
        AppUser courier = user(3L, "13800000003", UserRole.COURIER);
        order.assignCourier(courier);
        order.updateStatus(OrderStatus.PICKED_UP);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> orderService.complete(courier, 11L,
                        new OrderController.CompleteOrderRequest(order.getDeliveryCode())));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(OrderStatus.PICKED_UP, order.getStatus());
    }

    @Test
    void deliveryCompletionRejectsWrongDeliveryCode() {
        CampusOrder order = order(customer);
        AppUser courier = user(3L, "13800000003", UserRole.COURIER);
        order.assignCourier(courier);
        order.updateStatus(OrderStatus.PICKED_UP);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));
        when(proofRepository.existsByOrderAndType(order, OrderProofType.DELIVERY)).thenReturn(true);
        String wrongCode = order.getDeliveryCode().equals("000000") ? "000001" : "000000";

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> orderService.complete(courier, 11L, new OrderController.CompleteOrderRequest(wrongCode)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals(OrderStatus.PICKED_UP, order.getStatus());
    }

    @Test
    void customerCanReadDeliveryCodeAfterPickup() {
        CampusOrder order = order(customer);
        order.assignCourier(user(3L, "13800000003", UserRole.COURIER));
        order.updateStatus(OrderStatus.PICKED_UP);
        when(orderRepository.findById(11L)).thenReturn(Optional.of(order));

        OrderController.DeliveryCodeResponse response = orderService.deliveryCode(customer, 11L);

        assertEquals(order.getDeliveryCode(), response.deliveryCode());
    }

    @Test
    void manualQrOrderHidesDeliveryCodeUntilAdministratorConfirmsPayment() {
        CampusOrder order = order(customer);
        order.requireManualQrPayment();
        order.assignCourier(user(3L, "13800000003", UserRole.COURIER));
        order.updateStatus(OrderStatus.PICKED_UP);
        when(orderRepository.findById(11L)).thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> orderService.deliveryCode(customer, 11L));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("管理员确认到账后才能查看收货码", exception.getReason());
    }

    @Test
    void acceptingOrderNotifiesCustomer() {
        CampusOrder order = order(customer);
        AppUser courier = user(3L, "13800000003", UserRole.COURIER);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));
        when(userRepository.findLockedById(3L)).thenReturn(Optional.of(courier));
        when(settingsService.get()).thenReturn(new PlatformSettingsService.Settings("09:00–23:00", 10, 30, 2, 30, 120));

        orderService.accept(courier, 11L);

        verify(messageService).send(eq(customer), eq(order), eq(OrderMessageType.ORDER_ACCEPTED), anyString(), anyString());
    }

    @Test
    void unpaidOrderCannotBeAccepted() {
        CampusOrder order = order(customer);
        order.requirePayment();
        AppUser courier = user(3L, "13800000003", UserRole.COURIER);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> orderService.accept(courier, 11L));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("订单尚未完成支付", exception.getReason());
        verifyNoInteractions(messageService);
    }

    @Test
    void unapprovedCourierCannotAcceptOrder() {
        AppUser courier = user(3L, "13800000003", UserRole.COURIER);
        courier.markNewCourierUnapproved();

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> orderService.accept(courier, 11L));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(orderRepository);
    }

    @Test
    void offlineCourierCannotAcceptOrder() {
        AppUser courier = user(3L, "13800000003", UserRole.COURIER);
        courier.setCourierAcceptingOrders(false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> orderService.accept(courier, 11L));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("请先在骑手工作台上线接单", exception.getReason());
        verifyNoInteractions(orderRepository);
    }

    @Test
    void pickingUpOrderNotifiesCustomer() {
        CampusOrder order = order(customer);
        AppUser courier = user(3L, "13800000003", UserRole.COURIER);
        order.assignCourier(courier);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));
        when(proofRepository.existsByOrderAndType(order, OrderProofType.PICKUP)).thenReturn(true);

        orderService.markPickedUp(courier, 11L);

        verify(messageService).send(eq(customer), eq(order), eq(OrderMessageType.ORDER_PICKED_UP), anyString(), anyString());
    }

    @Test
    void completingOrderNotifiesCustomer() {
        CampusOrder order = order(customer);
        AppUser courier = user(3L, "13800000003", UserRole.COURIER);
        order.assignCourier(courier);
        order.updateStatus(OrderStatus.PICKED_UP);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));
        when(proofRepository.existsByOrderAndType(order, OrderProofType.DELIVERY)).thenReturn(true);

        orderService.complete(courier, 11L, new OrderController.CompleteOrderRequest(order.getDeliveryCode()));

        verify(messageService).send(eq(customer), eq(order), eq(OrderMessageType.ORDER_COMPLETED), anyString(), anyString());
    }

    @Test
    void cashOnDeliveryOrderCanBeAcceptedBeforeSettlement() {
        CampusOrder order = order(customer);
        order.requireCashOnDelivery();
        AppUser courier = user(3L, "13800000003", UserRole.COURIER);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));
        when(userRepository.findLockedById(3L)).thenReturn(Optional.of(courier));
        when(settingsService.get()).thenReturn(new PlatformSettingsService.Settings("09:00–23:00", 10, 30, 10, 30, 120));

        OrderController.OrderResponse response = orderService.accept(courier, 11L);

        assertEquals(OrderStatus.ACCEPTED, response.status());
        assertEquals(OrderPaymentMethod.CASH_ON_DELIVERY, response.paymentMethod());
        assertEquals(OrderPaymentStatus.UNPAID, response.paymentStatus());
    }

    @Test
    void completingCashOnDeliveryOrderRecordsOfflineSettlement() {
        CampusOrder order = order(customer);
        order.requireCashOnDelivery();
        AppUser courier = user(3L, "13800000003", UserRole.COURIER);
        order.assignCourier(courier);
        order.updateStatus(OrderStatus.PICKED_UP);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));
        when(proofRepository.existsByOrderAndType(order, OrderProofType.DELIVERY)).thenReturn(true);

        OrderController.OrderResponse response = orderService.complete(
                courier, 11L, new OrderController.CompleteOrderRequest(order.getDeliveryCode()));

        assertEquals(OrderStatus.COMPLETED, response.status());
        assertEquals(OrderPaymentStatus.PAID, response.paymentStatus());
        assertEquals(response.feeCents(), response.paidCents());
    }

    @Test
    void createOrderAppliesCouponToSettlement() {
        when(couponService.apply(customer, 51L, 200))
                .thenReturn(new CouponService.CouponApplication(100, "新用户立减券"));
        when(orderRepository.save(any(CampusOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderController.OrderResponse response = orderService.create(customer,
                new OrderController.CreateOrderRequest("测试商家", "1234", "东门", "3号楼", ServiceType.LOBBY, 51L, false));

        assertEquals(200, response.originalFeeCents());
        assertEquals(100, response.discountCents());
        assertEquals(100, response.feeCents());
        assertEquals(OrderPaymentMethod.PERSONAL_QR_MANUAL, response.paymentMethod());
        assertEquals(OrderPaymentStatus.UNPAID, response.paymentStatus());
        assertEquals(0, response.paidCents());
        assertEquals("新用户立减券", response.couponTitle());
    }

    @Test
    void enabledManualQrPaymentAppliesToEveryRegisteredCustomer() {
        AppUser anotherCustomer = user(9L, "18800000009", UserRole.CUSTOMER);
        when(couponService.apply(anotherCustomer, null, 200))
                .thenReturn(new CouponService.CouponApplication(0, null));
        when(orderRepository.save(any(CampusOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderController.OrderResponse response = orderService.create(anotherCustomer,
                new OrderController.CreateOrderRequest("测试商家", "1234", "东门", "3号楼",
                        ServiceType.LOBBY, null, false));

        assertEquals(OrderPaymentMethod.PERSONAL_QR_MANUAL, response.paymentMethod());
        assertEquals(OrderPaymentStatus.UNPAID, response.paymentStatus());
    }

    @Test
    void disabledManualQrPaymentFallsBackToCashOnDelivery() {
        OrderService disabledService = new OrderService(orderRepository, eventRepository, proofRepository,
                messageService, couponService, membershipService, userRepository,
                new com.fasterxml.jackson.databind.ObjectMapper(), paymentService, settingsService,
                certificationService,
                new com.zhangxuan.runruncampus.payment.ManualQrPaymentProperties(false));
        when(couponService.apply(customer, null, 200))
                .thenReturn(new CouponService.CouponApplication(0, null));
        when(orderRepository.save(any(CampusOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderController.OrderResponse response = disabledService.create(customer,
                new OrderController.CreateOrderRequest("测试商家", "1234", "东门", "3号楼",
                        ServiceType.LOBBY, null, false));

        assertEquals(OrderPaymentMethod.CASH_ON_DELIVERY, response.paymentMethod());
        assertEquals(OrderPaymentStatus.UNPAID, response.paymentStatus());
    }

    @Test
    void doorOrderWithMembershipChargesOneYuan() {
        when(membershipService.apply(customer, true))
                .thenReturn(new MembershipService.MembershipApplication(true, "学生月卡"));
        when(orderRepository.save(any(CampusOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderController.OrderResponse response = orderService.create(customer,
                new OrderController.CreateOrderRequest("测试商家", "1234", "东门", "3号楼", ServiceType.DOOR, null, true));

        assertEquals(300, response.originalFeeCents());
        assertEquals(200, response.discountCents());
        assertEquals(100, response.feeCents());
        assertTrue(response.membershipUsed());
        assertEquals("学生月卡", response.membershipPlanTitle());
        verifyNoInteractions(couponService);
    }

    @Test
    void expressOrderWithMembershipStillChargesThreeYuan() {
        when(membershipService.apply(customer, true))
                .thenReturn(new MembershipService.MembershipApplication(true, "学生月卡"));
        when(orderRepository.save(any(CampusOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderController.OrderResponse response = orderService.create(customer,
                new OrderController.CreateOrderRequest("夜间急送商家", "1234", "东门", "3号楼",
                        ServiceType.EXPRESS, null, true));

        assertEquals(500, response.originalFeeCents());
        assertEquals(200, response.discountCents());
        assertEquals(300, response.feeCents());
        assertTrue(response.membershipUsed());
    }

    @Test
    void packagePickupUsesConfiguredSizePrice() {
        when(couponService.apply(customer, null, 100))
                .thenReturn(new CouponService.CouponApplication(0, null));
        when(couponService.apply(customer, null, 200))
                .thenReturn(new CouponService.CouponApplication(0, null));
        when(couponService.apply(customer, null, 300))
                .thenReturn(new CouponService.CouponApplication(0, null));
        when(couponService.apply(customer, null, 400))
                .thenReturn(new CouponService.CouponApplication(0, null));
        when(couponService.apply(customer, null, 500))
                .thenReturn(new CouponService.CouponApplication(0, null));
        when(couponService.apply(customer, null, 800))
                .thenReturn(new CouponService.CouponApplication(0, null));
        when(orderRepository.save(any(CampusOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertPackagePrice(ServiceType.PACKAGE_SMALL, 100);
        assertPackagePrice(ServiceType.PACKAGE_MEDIUM, 200);
        assertPackagePrice(ServiceType.PACKAGE_MEDIUM_LARGE, 300);
        assertPackagePrice(ServiceType.PACKAGE_LARGE, 400);
        assertPackagePrice(ServiceType.PACKAGE_EXTRA_LARGE, 500);
        assertPackagePrice(ServiceType.PACKAGE_XL, 800);
    }

    @Test
    void meiyuanPackageAddsTwoYuanBeforeCoupon() {
        when(couponService.apply(customer, 51L, 300))
                .thenReturn(new CouponService.CouponApplication(100, "新用户立减券"));
        when(orderRepository.save(any(CampusOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderController.OrderResponse response = orderService.create(customer,
                new OrderController.CreateOrderRequest("菜鸟驿站", "1234", "校内快递驿站", "梅园3号楼 201",
                        ServiceType.PACKAGE_SMALL, 51L, false));

        assertEquals(300, response.originalFeeCents());
        assertEquals(100, response.discountCents());
        assertEquals(200, response.feeCents());
    }

    @Test
    void explicitStandardZoneDoesNotUseDestinationTextForPricing() {
        when(couponService.apply(customer, null, 100))
                .thenReturn(new CouponService.CouponApplication(0, null));
        when(orderRepository.save(any(CampusOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderController.OrderResponse response = orderService.create(customer,
                new OrderController.CreateOrderRequest("菜鸟驿站", "1234", "任意输入", "梅园字样备注",
                        ServiceType.PACKAGE_SMALL, null, false, PickupStation.CAMPUS_PACKAGE_STATION,
                        DeliveryZone.STANDARD));

        assertEquals(100, response.originalFeeCents());
        assertEquals(PickupStation.CAMPUS_PACKAGE_STATION, response.pickupStation());
        assertEquals(DeliveryZone.STANDARD, response.deliveryZone());
    }

    @Test
    void meiyuanDoesNotAddSurchargeToTakeout() {
        when(couponService.apply(customer, null, 200))
                .thenReturn(new CouponService.CouponApplication(0, null));
        when(orderRepository.save(any(CampusOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderController.OrderResponse response = orderService.create(customer,
                new OrderController.CreateOrderRequest("测试商家", "1234", "东门", "梅园3号楼 201",
                        ServiceType.LOBBY, null, false));

        assertEquals(200, response.originalFeeCents());
        assertEquals(200, response.feeCents());
    }

    @Test
    void meiyuanPackageAddsSurchargeBeforeMembershipDiscount() {
        when(membershipService.apply(customer, true))
                .thenReturn(new MembershipService.MembershipApplication(true, "学生月卡"));
        when(orderRepository.save(any(CampusOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderController.OrderResponse response = orderService.create(customer,
                new OrderController.CreateOrderRequest("菜鸟驿站", "1234", "校内快递驿站", "梅园3号楼 201",
                        ServiceType.PACKAGE_MEDIUM, null, true));

        assertEquals(400, response.originalFeeCents());
        assertEquals(200, response.discountCents());
        assertEquals(200, response.feeCents());
    }

    @Test
    void packageSizeChangeToExtraLargeKeepsMeiyuanSurcharge() {
        CampusOrder order = new CampusOrder("RP-PACKAGE-002", customer, "菜鸟驿站", "1234",
                "校内快递驿站", "梅园3号楼 201", ServiceType.PACKAGE_SMALL, 300, 0, null);
        AppUser courier = user(3L, "13800000003", UserRole.COURIER);
        order.assignCourier(courier);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));
        when(proofRepository.existsByOrderAndType(order, OrderProofType.SIZE_REVIEW)).thenReturn(true);

        orderService.confirmPackageSize(
                courier, 11L, new OrderController.ConfirmPackageSizeRequest(ServiceType.PACKAGE_XL));
        OrderController.OrderResponse approved = orderService.approvePackageSize(customer, 11L);

        assertEquals(1000, approved.originalFeeCents());
        assertEquals(1000, approved.feeCents());
    }

    @Test
    void packageSizeChangeToExtraLargeRequiresReviewProof() {
        CampusOrder order = new CampusOrder("RP-PACKAGE-003", customer, "菜鸟驿站", "1234",
                "校内快递驿站", "3号楼", ServiceType.PACKAGE_SMALL);
        AppUser courier = user(3L, "13800000003", UserRole.COURIER);
        order.assignCourier(courier);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> orderService.confirmPackageSize(
                        courier, 11L, new OrderController.ConfirmPackageSizeRequest(ServiceType.PACKAGE_XL)));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("提高包裹规格前请先上传规格核验照片", exception.getReason());
        assertNull(order.getProposedPackageServiceType());
    }

    @Test
    void smallPackageCannotUseMembershipEvenWithMeiyuanSurcharge() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> orderService.create(customer,
                        new OrderController.CreateOrderRequest("菜鸟驿站", "1234", "校内快递驿站", "梅园3号楼 201",
                                ServiceType.PACKAGE_SMALL, null, true)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("小件快递不支持使用月卡", exception.getReason());
        verifyNoInteractions(membershipService, couponService);
    }

    @Test
    void couponAndMembershipCannotBeCombined() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> orderService.create(customer,
                        new OrderController.CreateOrderRequest("测试商家", "1234", "东门", "3号楼",
                                ServiceType.LOBBY, 51L, true)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(couponService, membershipService);
    }

    @Test
    void customerCanRateOwnCompletedOrder() {
        CampusOrder order = completedOrder(customer);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));

        OrderController.OrderResponse response = orderService.rate(customer, 11L,
                new OrderController.CreateRatingRequest(5, "准时送达"));

        assertEquals(5, response.ratingScore());
        assertEquals("准时送达", response.ratingComment());
        assertTrue(response.ratedAt() != null);
    }

    @Test
    void customerCannotRateOrderBeforeCompletion() {
        CampusOrder order = order(customer);
        order.assignCourier(user(3L, "13800000003", UserRole.COURIER));
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> orderService.rate(customer, 11L, new OrderController.CreateRatingRequest(5, null)));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void anotherCustomerCannotRateOrder() {
        CampusOrder order = completedOrder(customer);
        AppUser otherCustomer = user(2L, "13800000002", UserRole.CUSTOMER);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> orderService.rate(otherCustomer, 11L, new OrderController.CreateRatingRequest(5, null)));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void completedOrderCanOnlyBeRatedOnce() {
        CampusOrder order = completedOrder(customer);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));
        orderService.rate(customer, 11L, new OrderController.CreateRatingRequest(5, null));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> orderService.rate(customer, 11L, new OrderController.CreateRatingRequest(4, null)));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void courierCanReadOwnRatingSummary() {
        AppUser courier = user(3L, "13800000003", UserRole.COURIER);
        when(orderRepository.averageRatingForCourier(courier)).thenReturn(4.5);
        when(orderRepository.countByCourierAndRatingScoreIsNotNull(courier)).thenReturn(2L);

        OrderController.CourierRatingResponse response = orderService.courierRating(courier);

        assertEquals(4.5, response.averageScore());
        assertEquals(2L, response.ratingCount());
    }

    private CampusOrder order(AppUser owner) {
        return new CampusOrder("RP-TEST-001", owner, "测试商家", "1234", "东门", "3号楼", ServiceType.LOBBY);
    }

    private CampusOrder packageOrder(AppUser owner, ServiceType serviceType) {
        return new CampusOrder("RP-PACKAGE-001", owner, "菜鸟驿站", "1234", "校内快递驿站", "3号楼", serviceType);
    }

    private void assertPackagePrice(ServiceType serviceType, int expectedFeeCents) {
        OrderController.OrderResponse response = orderService.create(customer,
                new OrderController.CreateOrderRequest("菜鸟驿站", "1234", "校内快递驿站", "3号楼",
                        serviceType, null, false));

        assertEquals(expectedFeeCents, response.originalFeeCents());
        assertEquals(expectedFeeCents, response.feeCents());
    }

    private CampusOrder completedOrder(AppUser owner) {
        CampusOrder order = order(owner);
        order.assignCourier(user(3L, "13800000003", UserRole.COURIER));
        order.updateStatus(OrderStatus.COMPLETED);
        return order;
    }

    private AppUser user(Long id, String phone, UserRole role) {
        AppUser user = new AppUser(phone, "password-hash", "测试账号", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
