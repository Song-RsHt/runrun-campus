package com.zhangxuan.runruncampus.service.admin;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.CampusOrder;
import com.zhangxuan.runruncampus.enums.AdminAuditAction;
import com.zhangxuan.runruncampus.enums.AdminAuditTargetType;
import com.zhangxuan.runruncampus.enums.OrderMessageType;
import com.zhangxuan.runruncampus.enums.OrderStatus;
import com.zhangxuan.runruncampus.enums.ServiceType;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.mapper.AdminDispatchMapper;
import com.zhangxuan.runruncampus.model.DispatchScoreCalculator;
import com.zhangxuan.runruncampus.model.Settings;
import com.zhangxuan.runruncampus.repository.OrderEventRepository;
import com.zhangxuan.runruncampus.repository.OrderRepository;
import com.zhangxuan.runruncampus.repository.UserRepository;
import com.zhangxuan.runruncampus.service.AdminAuditService;
import com.zhangxuan.runruncampus.service.AdminDispatchService;
import com.zhangxuan.runruncampus.service.CourierCertificationService;
import com.zhangxuan.runruncampus.service.OrderMessageService;
import com.zhangxuan.runruncampus.service.PlatformSettingsService;
import com.zhangxuan.runruncampus.service.impl.AdminDispatchServiceImpl;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import com.zhangxuan.runruncampus.exception.BusinessFailure;
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
    service =
        new AdminDispatchServiceImpl(
            new AdminDispatchMapper(jdbc),
            orders,
            users,
            events,
            messages,
            audit,
            settings,
            certifications);
    Mockito.lenient()
        .when(settings.get())
        .thenReturn(new Settings("09:00–23:00", 10, 30, 2, 30, 120));
    Mockito.lenient()
        .when(certifications.isEligible(ArgumentMatchers.any(AppUser.class)))
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
    verify(events)
        .save(
            argThat(
                event ->
                    event.getActor() == administrator
                        && event.getStatus() == OrderStatus.ACCEPTED));
    verify(messages)
        .send(
            eq(customer),
            eq(order),
            eq(OrderMessageType.ORDER_ACCEPTED),
            eq("骑手已接单"),
            argThat(content -> content.contains("骑手一号")));
    verify(messages)
        .send(
            eq(courier),
            eq(order),
            eq(OrderMessageType.ORDER_ACCEPTED),
            eq("运营已派单"),
            argThat(content -> content.contains("RP-DISPATCH-1")));
    verify(audit)
        .record(
            administrator,
            AdminAuditAction.ORDER_DISPATCHED,
            AdminAuditTargetType.ORDER,
            11L,
            "将订单 RP-DISPATCH-1 派给骑手 骑手一号（#3）");
  }

  @Test
  void offlineCourierIsRejectedAtConfirmationTime() {
    courier.setCourierAcceptingOrders(false);
    CampusOrder order = order();
    when(orders.findLockedById(11L)).thenReturn(Optional.of(order));
    when(users.findLockedById(3L)).thenReturn(Optional.of(courier));
    BusinessFailure exception =
        assertThrows(BusinessFailure.class, () -> service.dispatch(administrator, 11L, 3L));
    assertEquals(HttpStatus.CONFLICT.value(), exception.status());
    assertEquals(OrderStatus.PENDING_ASSIGNMENT, order.getStatus());
    verifyNoInteractions(events, messages, audit);
  }

  @Test
  void acceptedOrderIsRejectedBeforeReadingCourier() {
    CampusOrder order = order();
    order.assignCourier(courier);
    when(orders.findLockedById(11L)).thenReturn(Optional.of(order));
    BusinessFailure exception =
        assertThrows(BusinessFailure.class, () -> service.dispatch(administrator, 11L, 3L));
    assertEquals(HttpStatus.CONFLICT.value(), exception.status());
    verifyNoInteractions(users, events, messages, audit);
  }

  @Test
  void adminCannotOverrideConfiguredCourierCapacity() {
    CampusOrder order = order();
    when(orders.findLockedById(11L)).thenReturn(Optional.of(order));
    when(users.findLockedById(3L)).thenReturn(Optional.of(courier));
    when(orders.countByCourierAndStatusIn(
            courier, List.of(OrderStatus.ACCEPTED, OrderStatus.PICKED_UP)))
        .thenReturn(2L);
    when(settings.get()).thenReturn(new Settings("09:00–23:00", 10, 30, 2, 30, 120));
    BusinessFailure failure =
        assertThrows(BusinessFailure.class, () -> service.dispatch(administrator, 11L, 3L));
    assertEquals(HttpStatus.CONFLICT.value(), failure.status());
    verifyNoInteractions(events, messages, audit);
  }

  @Test
  void adminCannotDispatchToCourierWithExpiredCertification() {
    CampusOrder order = order();
    when(orders.findLockedById(11L)).thenReturn(Optional.of(order));
    when(users.findLockedById(3L)).thenReturn(Optional.of(courier));
    when(certifications.isEligible(courier)).thenReturn(false);
    BusinessFailure failure =
        assertThrows(BusinessFailure.class, () -> service.dispatch(administrator, 11L, 3L));
    assertEquals(HttpStatus.CONFLICT.value(), failure.status());
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
    verify(audit)
        .record(
            eq(administrator),
            eq(AdminAuditAction.ORDER_REASSIGNED),
            eq(AdminAuditTargetType.ORDER),
            eq(11L),
            argThat(summary -> summary.contains("骑手一号")));
  }

  @Test
  void recommendationKeepsIdleCourierAheadOfBusyExperiencedCourier() {
    int idleNewcomer = DispatchScoreCalculator.recommendationScore(0, 0, 0, 0, null, 0);
    int busyVeteran = DispatchScoreCalculator.recommendationScore(1, 120, 100, 50, 5.0, 100);
    assertEquals(60, idleNewcomer);
    assertEquals(55, busyVeteran);
  }

  @Test
  void recommendationUsesVerifiedExperienceAndIgnoresSparseRatingBonus() {
    int sparseRating = DispatchScoreCalculator.recommendationScore(0, 60, 20, 6, 5.0, 2);
    int verifiedRating = DispatchScoreCalculator.recommendationScore(0, 60, 20, 6, 5.0, 3);
    assertEquals(73, sparseRating);
    assertEquals(83, verifiedRating);
  }

  @Test
  void recommendationScoreKeepsIdleCourierAheadOfBusyExperiencedCourier() {
    int idleNewCourier = DispatchScoreCalculator.recommendationScore(0, 0, 0, 0, null, 0);
    int busyTopPerformer = DispatchScoreCalculator.recommendationScore(1, 120, 100, 100, 5.0, 100);
    assertTrue(idleNewCourier > busyTopPerformer);
    assertEquals(60, idleNewCourier);
    assertEquals(55, busyTopPerformer);
  }

  @Test
  void recommendationScoreRequiresEnoughRatingsBeforeUsingRatingBonus() {
    int sparse = DispatchScoreCalculator.recommendationScore(0, 0, 0, 0, 5.0, 2);
    int established = DispatchScoreCalculator.recommendationScore(0, 0, 0, 0, 5.0, 3);
    assertEquals(60, sparse);
    assertEquals(70, established);
  }

  private CampusOrder order() {
    CampusOrder order =
        new CampusOrder("RP-DISPATCH-1", customer, "一食堂", "1234", "东门", "3号楼", ServiceType.LOBBY);
    ReflectionTestUtils.setField(order, "id", 11L);
    return order;
  }

  private AppUser user(Long id, UserRole role, String name) {
    AppUser user = new AppUser("1380000000" + id, "hash", name, role);
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }
}
