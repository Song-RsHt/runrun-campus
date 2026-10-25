package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.message.OrderMessageService;
import com.zhangxuan.runruncampus.message.OrderMessageType;
import com.zhangxuan.runruncampus.order.CampusOrder;
import com.zhangxuan.runruncampus.order.OrderEventRepository;
import com.zhangxuan.runruncampus.order.OrderRepository;
import com.zhangxuan.runruncampus.order.OrderStatus;
import com.zhangxuan.runruncampus.order.ServiceType;
import com.zhangxuan.runruncampus.api.PlatformSettingsService;
import com.zhangxuan.runruncampus.courier.CourierCertificationService;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRepository;
import com.zhangxuan.runruncampus.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDispatchServiceTest {
    @Mock JdbcTemplate jdbc;
    @Mock OrderRepository orders;
    @Mock UserRepository users;
    @Mock OrderEventRepository events;
    @Mock OrderMessageService messages;
    @Mock AdminAuditService audit;
    @Mock PlatformSettingsService settings;
    @Mock CourierCertificationService certifications;

    private AdminDispatchService service;
    private AppUser administrator;
    private AppUser customer;
    private AppUser courier;

    @BeforeEach
    void setUp() {
        service = new AdminDispatchService(jdbc, orders, users, events, messages, audit, settings, certifications);
        org.mockito.Mockito.lenient().when(settings.get())
                .thenReturn(new PlatformSettingsService.Settings("09:00–23:00", 10, 30, 2, 30, 120));
        org.mockito.Mockito.lenient().when(certifications.isEligible(org.mockito.ArgumentMatchers.any(AppUser.class)))
                .thenAnswer(call -> ((AppUser) call.getArgument(0)).isCourierApproved());
        administrator = user(1L, UserRole.ADMIN, "运营一号");
        customer = user(2L, UserRole.CUSTOMER, "用户一号");
        courier = user(3L, UserRole.COURIER, "骑手一号");
        courier.approveCourier();
        courier.setCourierAcceptingOrders(true);
    }

    @Test
    void dispatchAssignsCourierCreatesEventNotifiesBothSidesAndAudits() {
        CampusOrder order = order();
        when(orders.findLockedById(11L)).thenReturn(Optional.of(order));
        when(users.findLockedById(3L)).thenReturn(Optional.of(courier));

        var result = service.dispatch(administrator, 11L, 3L);

        assertEquals(OrderStatus.ACCEPTED, result.status());
        assertSame(courier, order.getCourier());
        verify(events).save(argThat(event -> event.getActor() == administrator
                && event.getStatus() == OrderStatus.ACCEPTED));
        verify(messages).send(eq(customer), eq(order), eq(OrderMessageType.ORDER_ACCEPTED),
                eq("骑手已接单"), argThat(content -> content.contains("骑手一号")));
        verify(messages).send(eq(courier), eq(order), eq(OrderMessageType.ORDER_ACCEPTED),
                eq("运营已派单"), argThat(content -> content.contains("RP-DISPATCH-1")));
        verify(audit).record(administrator, AdminAuditAction.ORDER_DISPATCHED, AdminAuditTargetType.ORDER,
                11L, "将订单 RP-DISPATCH-1 派给骑手 骑手一号（#3）");
    }

    @Test
    void offlineCourierIsRejectedAtConfirmationTime() {
        courier.setCourierAcceptingOrders(false);
        CampusOrder order = order();
        when(orders.findLockedById(11L)).thenReturn(Optional.of(order));
        when(users.findLockedById(3L)).thenReturn(Optional.of(courier));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.dispatch(administrator, 11L, 3L));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(OrderStatus.PENDING_ASSIGNMENT, order.getStatus());
        verifyNoInteractions(events, messages, audit);
    }

    @Test
    void acceptedOrderIsRejectedBeforeReadingCourier() {
        CampusOrder order = order();
        order.assignCourier(courier);
        when(orders.findLockedById(11L)).thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.dispatch(administrator, 11L, 3L));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verifyNoInteractions(users, events, messages, audit);
    }

    @Test
    void adminCannotOverrideConfiguredCourierCapacity() {
        CampusOrder order = order();
        when(orders.findLockedById(11L)).thenReturn(Optional.of(order));
        when(users.findLockedById(3L)).thenReturn(Optional.of(courier));
        when(orders.countByCourierAndStatusIn(courier,
                java.util.List.of(OrderStatus.ACCEPTED, OrderStatus.PICKED_UP))).thenReturn(2L);
        when(settings.get()).thenReturn(new PlatformSettingsService.Settings("09:00–23:00", 10, 30, 2, 30, 120));

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.dispatch(administrator, 11L, 3L));

        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        verifyNoInteractions(events, messages, audit);
    }

    @Test
    void adminCannotDispatchToCourierWithExpiredCertification() {
        CampusOrder order = order();
        when(orders.findLockedById(11L)).thenReturn(Optional.of(order));
        when(users.findLockedById(3L)).thenReturn(Optional.of(courier));
        when(certifications.isEligible(courier)).thenReturn(false);

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.dispatch(administrator, 11L, 3L));

        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        verifyNoInteractions(events, messages, audit);
    }

    @Test
    void reassignmentRequiresAdminConfirmationAndAuditsNewCourier() {
        CampusOrder order = order();
        order.assignCourier(user(4L, UserRole.COURIER, "原骑手"));
        order.requestReassignment();
        when(orders.findLockedById(11L)).thenReturn(Optional.of(order));
        when(users.findLockedById(3L)).thenReturn(Optional.of(courier));

        var result = service.dispatch(administrator, 11L, 3L);

        assertEquals(OrderStatus.ACCEPTED, result.status());
        assertSame(courier, order.getCourier());
        verify(audit).record(eq(administrator), eq(AdminAuditAction.ORDER_REASSIGNED),
                eq(AdminAuditTargetType.ORDER), eq(11L), argThat(summary -> summary.contains("骑手一号")));
    }

    @Test
    void recommendationKeepsIdleCourierAheadOfBusyExperiencedCourier() {
        int idleNewcomer = AdminDispatchService.recommendationScore(0, 0, 0, 0, null, 0);
        int busyVeteran = AdminDispatchService.recommendationScore(1, 120, 100, 50, 5.0, 100);

        assertEquals(60, idleNewcomer);
        assertEquals(55, busyVeteran);
    }

    @Test
    void recommendationUsesVerifiedExperienceAndIgnoresSparseRatingBonus() {
        int sparseRating = AdminDispatchService.recommendationScore(0, 60, 20, 6, 5.0, 2);
        int verifiedRating = AdminDispatchService.recommendationScore(0, 60, 20, 6, 5.0, 3);

        assertEquals(73, sparseRating);
        assertEquals(83, verifiedRating);
    }

    @Test
    void recommendationScoreKeepsIdleCourierAheadOfBusyExperiencedCourier() {
        int idleNewCourier = AdminDispatchService.recommendationScore(0, 0, 0, 0, null, 0);
        int busyTopPerformer = AdminDispatchService.recommendationScore(1, 120, 100, 100, 5.0, 100);

        assertTrue(idleNewCourier > busyTopPerformer);
        assertEquals(60, idleNewCourier);
        assertEquals(55, busyTopPerformer);
    }

    @Test
    void recommendationScoreRequiresEnoughRatingsBeforeUsingRatingBonus() {
        int sparse = AdminDispatchService.recommendationScore(0, 0, 0, 0, 5.0, 2);
        int established = AdminDispatchService.recommendationScore(0, 0, 0, 0, 5.0, 3);

        assertEquals(60, sparse);
        assertEquals(70, established);
    }

    private CampusOrder order() {
        CampusOrder order = new CampusOrder("RP-DISPATCH-1", customer, "一食堂", "1234", "东门",
                "3号楼", ServiceType.LOBBY);
        ReflectionTestUtils.setField(order, "id", 11L);
        return order;
    }

    private AppUser user(Long id, UserRole role, String name) {
        AppUser user = new AppUser("1380000000" + id, "hash", name, role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
