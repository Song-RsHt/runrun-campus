package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.message.OrderMessageService;
import com.zhangxuan.runruncampus.message.OrderMessageType;
import com.zhangxuan.runruncampus.order.CampusOrder;
import com.zhangxuan.runruncampus.order.OrderEventRepository;
import com.zhangxuan.runruncampus.order.OrderPaymentStatus;
import com.zhangxuan.runruncampus.order.OrderProofRepository;
import com.zhangxuan.runruncampus.order.OrderProofType;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOrderLifecycleServiceTest {
    @Mock OrderRepository orders;
    @Mock OrderProofRepository proofs;
    @Mock OrderEventRepository events;
    @Mock OrderMessageService messages;
    @Mock AdminAuditService audit;

    private AdminOrderLifecycleService service;
    private AppUser admin;
    private AppUser customer;
    private AppUser courier;
    private CampusOrder order;

    @BeforeEach
    void setUp() {
        service = new AdminOrderLifecycleService(orders, proofs, events, messages, audit);
        admin = user(1, UserRole.ADMIN);
        customer = user(2, UserRole.CUSTOMER);
        courier = user(3, UserRole.COURIER);
        order = new CampusOrder("RP-LIFECYCLE-1", customer, "一食堂", "1234", "东门", "三号楼", ServiceType.LOBBY);
        ReflectionTestUtils.setField(order, "id", 11L);
        org.mockito.Mockito.lenient().when(orders.findLockedById(11L)).thenReturn(Optional.of(order));
    }

    @Test
    void onlySuperAdminCanOverrideCompletionWhileOperationsMayArchive() {
        assertTrue(AdminRole.SUPER_ADMIN.permits(AdminPermission.ORDER_FORCE_COMPLETE));
        assertFalse(AdminRole.OPERATIONS.permits(AdminPermission.ORDER_FORCE_COMPLETE));
        assertTrue(AdminRole.OPERATIONS.permits(AdminPermission.ORDER_ARCHIVE));
        assertFalse(AdminRole.SUPPORT.permits(AdminPermission.ORDER_ARCHIVE));
    }

    @Test
    void forceCompletionRequiresConfirmedPayment() {
        pickedUp();
        when(proofs.existsByOrderAndType(order, OrderProofType.DELIVERY)).thenReturn(true);

        var error = assertThrows(ResponseStatusException.class,
                () -> service.forceComplete(admin, 11L, "已核对送达照片和收款记录"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals(OrderStatus.PICKED_UP, order.getStatus());
        verifyNoInteractions(events, messages, audit);
    }

    @Test
    void forceCompletionRequiresDeliveryProof() {
        pickedUp();
        order.recordPayment(order.getFeeCents());

        var error = assertThrows(ResponseStatusException.class,
                () -> service.forceComplete(admin, 11L, "已核对送达照片和收款记录"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verifyNoInteractions(events, messages, audit);
    }

    @Test
    void unpaidCashOnDeliveryCannotBeForcedComplete() {
        pickedUp();
        order.requireCashOnDelivery();
        when(proofs.existsByOrderAndType(order, OrderProofType.DELIVERY)).thenReturn(true);

        var error = assertThrows(ResponseStatusException.class,
                () -> service.forceComplete(admin, 11L, "已核对送达照片和收款记录"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals(OrderStatus.PICKED_UP, order.getStatus());
    }

    @Test
    void forceCompletionRecordsAdminAsActorWithoutUsingDeliveryCode() {
        pickedUp();
        order.recordPayment(order.getFeeCents());
        when(proofs.existsByOrderAndType(order, OrderProofType.DELIVERY)).thenReturn(true);

        service.forceComplete(admin, 11L, "已核对送达照片和收款记录");

        assertEquals(OrderStatus.COMPLETED, order.getStatus());
        verify(events).save(org.mockito.ArgumentMatchers.argThat(event -> event.getActor() == admin
                && event.getStatus() == OrderStatus.COMPLETED));
        verify(messages).send(eq(customer), eq(order), eq(OrderMessageType.ORDER_COMPLETED), any(), any());
        verify(audit).record(eq(admin), eq(AdminAuditAction.ORDER_FORCE_COMPLETED), eq(AdminAuditTargetType.ORDER),
                eq(11L), any());
    }

    @Test
    void onlyTerminalSettledOrdersCanBeArchivedAndRestored() {
        var error = assertThrows(ResponseStatusException.class,
                () -> service.archive(admin, 11L, "测试单不再显示"));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());

        order.updateStatus(OrderStatus.COMPLETED);
        service.archive(admin, 11L, "测试单不再显示");
        assertNotNull(order.getArchivedAt());
        verify(audit).record(eq(admin), eq(AdminAuditAction.ORDER_ARCHIVED), eq(AdminAuditTargetType.ORDER),
                eq(11L), any());

        assertThrows(ResponseStatusException.class, () -> service.archive(admin, 11L, "重复归档订单"));

        service.unarchive(admin, 11L);
        assertNull(order.getArchivedAt());
        verify(audit).record(eq(admin), eq(AdminAuditAction.ORDER_UNARCHIVED), eq(AdminAuditTargetType.ORDER),
                eq(11L), any());
    }

    @Test
    void refundingOrderCannotBeHidden() {
        order.updateStatus(OrderStatus.CANCELLED);
        ReflectionTestUtils.setField(order, "paymentStatus", OrderPaymentStatus.REFUNDING);

        var error = assertThrows(ResponseStatusException.class,
                () -> service.archive(admin, 11L, "测试单不再显示"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertNull(order.getArchivedAt());
    }

    @Test
    void pendingCourierSettlementCannotBeHidden() {
        order.requireManualQrPayment();
        order.confirmManualPayment(admin);
        order.updateStatus(OrderStatus.COMPLETED);
        order.markCourierSettlementPending();

        var error = assertThrows(ResponseStatusException.class,
                () -> service.archive(admin, 11L, "等待骑手结算前不应归档"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertNull(order.getArchivedAt());
    }

    private void pickedUp() {
        order.requirePayment();
        order.assignCourier(courier);
        order.updateStatus(OrderStatus.PICKED_UP);
    }

    private AppUser user(long id, UserRole role) {
        AppUser value = new AppUser("1380000000" + id, "hash", "用户" + id, role);
        ReflectionTestUtils.setField(value, "id", id);
        return value;
    }
}
