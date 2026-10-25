package com.zhangxuan.runruncampus.payment;

import com.zhangxuan.runruncampus.admin.AdminAuditService;
import com.zhangxuan.runruncampus.message.OrderMessageService;
import com.zhangxuan.runruncampus.order.CampusOrder;
import com.zhangxuan.runruncampus.order.CourierSettlementStatus;
import com.zhangxuan.runruncampus.order.ManualPaymentStatus;
import com.zhangxuan.runruncampus.order.OrderPaymentStatus;
import com.zhangxuan.runruncampus.order.OrderRepository;
import com.zhangxuan.runruncampus.order.OrderStatus;
import com.zhangxuan.runruncampus.order.ServiceType;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualQrPaymentServiceTest {
    @Mock private OrderRepository orderRepository;
    @Mock private OrderMessageService messageService;
    @Mock private AdminAuditService auditService;

    private ManualQrPaymentService service;
    private AppUser customer;
    private AppUser courier;
    private AppUser administrator;
    private CampusOrder order;

    @BeforeEach
    void setUp() {
        service = new ManualQrPaymentService(orderRepository, messageService, auditService);
        customer = user(1L, "用户", UserRole.CUSTOMER);
        courier = user(2L, "骑手", UserRole.COURIER);
        administrator = user(3L, "运营", UserRole.ADMIN);
        order = new CampusOrder("RP-MANUAL-001", customer, "快递驿站", "1234", "东门", "1号楼",
                ServiceType.PACKAGE_MEDIUM);
        ReflectionTestUtils.setField(order, "id", 11L);
        order.requireManualQrPayment();
        order.assignCourier(courier);
        order.updateStatus(OrderStatus.PICKED_UP);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));
    }

    @Test
    void paymentNeedsCourierRequestCustomerSubmissionAndAdminConfirmation() {
        service.requestPayment(courier, 11L);
        assertEquals(ManualPaymentStatus.AWAITING_PAYMENT, order.getManualPaymentStatus());

        service.submitPayment(customer, 11L, "微信昵称尾号28");
        assertEquals(ManualPaymentStatus.SUBMITTED, order.getManualPaymentStatus());
        assertEquals(OrderPaymentStatus.UNPAID, order.getPaymentStatus());

        service.confirmPayment(administrator, 11L);
        assertEquals(ManualPaymentStatus.CONFIRMED, order.getManualPaymentStatus());
        assertEquals(OrderPaymentStatus.PAID, order.getPaymentStatus());
        assertEquals(order.getFeeCents(), order.getPaidCents());
        assertEquals(administrator, order.getPaymentReviewer());
        assertNotNull(order.getPaymentReviewedAt());
        verify(auditService).record(any(), any(), any(), any(), any());
    }

    @Test
    void rejectedClaimCanBeCorrectedAndSubmittedAgain() {
        service.requestPayment(courier, 11L);
        service.submitPayment(customer, 11L, "错误昵称");
        service.rejectPayment(administrator, 11L, "未查询到对应到账记录");

        assertEquals(ManualPaymentStatus.REJECTED, order.getManualPaymentStatus());
        assertEquals("未查询到对应到账记录", order.getPaymentReviewNote());

        service.submitPayment(customer, 11L, "正确昵称");
        assertEquals(ManualPaymentStatus.SUBMITTED, order.getManualPaymentStatus());
        assertEquals("正确昵称", order.getPayerReference());
    }

    @Test
    void otherCustomerCannotSubmitPaymentClaim() {
        service.requestPayment(courier, 11L);
        AppUser stranger = user(4L, "其他用户", UserRole.CUSTOMER);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.submitPayment(stranger, 11L, "冒名提交"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals(ManualPaymentStatus.AWAITING_PAYMENT, order.getManualPaymentStatus());
    }

    @Test
    void settlementCanOnlyBeRecordedAfterCompletedDelivery() {
        service.requestPayment(courier, 11L);
        service.submitPayment(customer, 11L, "微信昵称尾号28");
        service.confirmPayment(administrator, 11L);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.confirmCourierSettlement(administrator, 11L));
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        order.updateStatus(OrderStatus.COMPLETED);
        order.markCourierSettlementPending();
        service.confirmCourierSettlement(administrator, 11L);
        assertEquals(CourierSettlementStatus.SETTLED, order.getCourierSettlementStatus());
        assertNotNull(order.getCourierSettledAt());
    }

    private AppUser user(Long id, String name, UserRole role) {
        AppUser user = new AppUser("1380000000" + id, "hash", name, role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
