package com.zhangxuan.runruncampus.order;

import com.zhangxuan.runruncampus.message.OrderMessageService;
import com.zhangxuan.runruncampus.message.OrderMessageType;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CourierReassignmentServiceTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderProofRepository proofs = mock(OrderProofRepository.class);
    private final OrderIssueRepository issues = mock(OrderIssueRepository.class);
    private final OrderEventRepository events = mock(OrderEventRepository.class);
    private final OrderMessageService messages = mock(OrderMessageService.class);
    private final CourierReassignmentService service = new CourierReassignmentService(orders, proofs, issues, events, messages);
    private final AppUser customer = user(1L, UserRole.CUSTOMER);
    private final AppUser courier = user(2L, UserRole.COURIER);

    @Test
    void acceptedOrderMovesToAdminOnlyReassignmentQueueAndRevokesOldCourier() {
        CampusOrder order = order();
        order.assignCourier(courier);
        when(orders.findLockedById(11L)).thenReturn(Optional.of(order));
        when(issues.save(any(OrderIssue.class))).thenAnswer(call -> call.getArgument(0));

        var result = service.reportUnable(courier, 11L, "临时身体不适，无法前往取件");

        assertEquals(OrderStatus.PENDING_REASSIGNMENT, result.status());
        assertNull(order.getCourier());
        verify(events).save(any(OrderEvent.class));
        verify(messages).send(eq(customer), eq(order), eq(OrderMessageType.ORDER_ISSUE),
                eq("骑手无法履约，等待运营改派"), any(String.class));
    }

    @Test
    void pickedUpOrderCannotUseAutomaticReassignment() {
        CampusOrder order = order();
        order.assignCourier(courier);
        order.updateStatus(OrderStatus.PICKED_UP);
        when(orders.findLockedById(11L)).thenReturn(Optional.of(order));

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.reportUnable(courier, 11L, "无法配送"));

        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        verifyNoInteractions(issues, events, messages);
    }

    @Test
    void pickupProofPreventsReleaseEvenBeforePickupStatusChange() {
        CampusOrder order = order();
        order.assignCourier(courier);
        when(orders.findLockedById(11L)).thenReturn(Optional.of(order));
        when(proofs.existsByOrderAndType(order, OrderProofType.PICKUP)).thenReturn(true);

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.reportUnable(courier, 11L, "无法配送"));

        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        verifyNoInteractions(issues, events, messages);
    }

    @Test
    void packageSizeProofPreventsNewCourierFromInheritingOldEvidence() {
        CampusOrder order = order();
        order.assignCourier(courier);
        when(orders.findLockedById(11L)).thenReturn(Optional.of(order));
        when(proofs.existsByOrderAndType(order, OrderProofType.SIZE_REVIEW)).thenReturn(true);

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.reportUnable(courier, 11L, "无法配送"));

        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        verifyNoInteractions(issues, events, messages);
    }

    @Test
    void unassignedCourierCannotReleaseAnotherCouriersOrder() {
        CampusOrder order = order();
        order.assignCourier(user(3L, UserRole.COURIER));
        when(orders.findLockedById(11L)).thenReturn(Optional.of(order));

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.reportUnable(courier, 11L, "无法配送"));

        assertEquals(HttpStatus.FORBIDDEN, failure.getStatusCode());
        verifyNoInteractions(issues, events, messages);
    }

    private CampusOrder order() {
        CampusOrder order = new CampusOrder("RP-REASSIGN-1", customer, "商家", "1234", "东门", "宿舍", ServiceType.LOBBY);
        ReflectionTestUtils.setField(order, "id", 11L);
        return order;
    }

    private AppUser user(Long id, UserRole role) {
        AppUser user = new AppUser("1380000000" + id, "hash", "账号" + id, role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
