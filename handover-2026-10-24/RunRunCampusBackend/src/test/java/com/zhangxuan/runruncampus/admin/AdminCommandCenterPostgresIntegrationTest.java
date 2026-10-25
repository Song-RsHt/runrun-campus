package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.order.*;
import com.zhangxuan.runruncampus.user.*;
import com.zhangxuan.runruncampus.api.PlatformSettingsService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "app.proof-storage.local-directory=target/test-command-proofs")
@ActiveProfiles("test")
@Testcontainers
@Transactional
class AdminCommandCenterPostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired AdminCommandCenterService service;
    @Autowired AdminDispatchService dispatchService;
    @Autowired UserRepository users;
    @Autowired OrderRepository orders;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformSettingsService settings;
    private static final Instant NOW = Instant.parse("2026-09-13T04:00:00Z");

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void newAndExistingSettingsBecomeTenWhileRemainingAdjustable() {
        assertEquals(10, settings.get().maxActiveOrders());
        assertEquals(30, settings.get().issuePendingMinutes());
        assertEquals(120, settings.get().issueProcessingMinutes());
        String schema = "courier_limit_upgrade";
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).target("30").load().migrate();
        jdbc.update("update " + schema + ".platform_settings set max_active_orders = 4 where id = 1");

        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).load().migrate();

        assertEquals(10, jdbc.queryForObject("select max_active_orders from " + schema
                + ".platform_settings where id = 1", Integer.class));
        jdbc.update("update " + schema + ".platform_settings set max_active_orders = 3 where id = 1");
        assertEquals(3, jdbc.queryForObject("select max_active_orders from " + schema
                + ".platform_settings where id = 1", Integer.class));
    }

    @Test
    void waitingUsesPaymentTimeExcludesUnpaidAndIncludesExactThreshold() {
        AppUser customer = user(UserRole.CUSTOMER);
        long boundary = order(customer, "PENDING_ASSIGNMENT", "PAID", NOW.minusSeconds(7200));
        payment(boundary, customer, NOW.minusSeconds(600));
        long fresh = order(customer, "PENDING_ASSIGNMENT", "PAID", NOW.minusSeconds(7200));
        payment(fresh, customer, NOW.minusSeconds(599));
        order(customer, "PENDING_ASSIGNMENT", "UNPAID", NOW.minusSeconds(7200));
        order(customer, "CANCELLED", "PAID", NOW.minusSeconds(7200));
        try (var time = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            time.when(Instant::now).thenReturn(NOW);
            var result = service.snapshot(AdminRole.OPERATIONS, AdminCommandCenterService.Queue.LATE_WAITING, 0, 0);
            assertEquals(2, result.orders().waiting());
            assertEquals(1, result.orders().lateWaiting());
            assertEquals(boundary, result.orders().items().get(0).id());
            assertFalse(result.orders().items().get(0).estimated());
        }
    }

    @Test
    void unpaidCashOnDeliveryOrderAppearsInWaitingQueueFromCreationTime() {
        AppUser customer = user(UserRole.CUSTOMER);
        long id = order(customer, "PENDING_ASSIGNMENT", "UNPAID", NOW.minusSeconds(300));
        jdbc.update("update campus_orders set payment_method = 'CASH_ON_DELIVERY' where id = ?", id);

        try (var time = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            time.when(Instant::now).thenReturn(NOW);
            var result = service.snapshot(AdminRole.OPERATIONS,
                    AdminCommandCenterService.Queue.WAITING, 0, 0).orders();

            assertEquals(1, result.waiting());
            assertEquals(id, result.items().get(0).id());
            assertEquals(NOW.minusSeconds(300), result.items().get(0).stageStart());
            assertFalse(result.items().get(0).estimated());
        }
    }

    @Test
    void reassignmentUsesReleaseEventAndOnlyAppearsInAdminWaitingQueue() {
        AppUser customer = user(UserRole.CUSTOMER);
        long id = order(customer, "PENDING_REASSIGNMENT", "PAID", NOW.minusSeconds(7200));
        event(id, customer, "ACCEPTED", NOW.minusSeconds(3600));
        event(id, customer, "PENDING_REASSIGNMENT", NOW.minusSeconds(300));
        try (var time = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            time.when(Instant::now).thenReturn(NOW);
            var waiting = service.snapshot(AdminRole.OPERATIONS, AdminCommandCenterService.Queue.WAITING, 0, 0).orders();
            assertEquals(1, waiting.waiting());
            assertEquals(0, waiting.active());
            assertEquals(id, waiting.items().get(0).id());
            assertEquals(NOW.minusSeconds(300), waiting.items().get(0).stageStart());
            assertFalse(waiting.items().get(0).estimated());
        }
    }

    @Test
    void configuredThresholdChangesReminderWithoutChangingOrderStatus() {
        AppUser customer = user(UserRole.CUSTOMER);
        long id = order(customer, "PENDING_ASSIGNMENT", "PAID", NOW.minusSeconds(3600));
        payment(id, customer, NOW.minusSeconds(420));
        AppUser administrator = user(UserRole.ADMIN);
        settings.update(administrator, new PlatformSettingsService.Settings("10:00–22:00", 5, 30, 2, 30, 120));
        try (var time = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            time.when(Instant::now).thenReturn(NOW);
            var result = service.snapshot(AdminRole.OPERATIONS, AdminCommandCenterService.Queue.LATE_WAITING, 0, 0);
            assertEquals(5, result.waitMinutes());
            assertEquals(1, result.orders().lateWaiting());
            assertEquals(id, result.orders().items().get(0).id());
            assertEquals("PENDING_ASSIGNMENT", jdbc.queryForObject(
                    "select status from campus_orders where id = ?", String.class, id));
        }
    }

    @Test
    void configuredSingleOrderLimitRemovesBusyCourierFromCandidates() {
        AppUser busy = onlineCourier();
        AppUser idle = onlineCourier();
        AppUser customer = user(UserRole.CUSTOMER);
        long active = order(customer, "ACCEPTED", "PAID", NOW);
        jdbc.update("update campus_orders set courier_id = ? where id = ?", busy.getId(), active);
        long waiting = order(customer, "PENDING_ASSIGNMENT", "PAID", NOW);
        settings.update(user(UserRole.ADMIN),
                new PlatformSettingsService.Settings("09:00–23:00", 10, 30, 1, 30, 120));

        var result = dispatchService.candidates(waiting, 0);

        assertEquals(1, result.totalElements());
        assertEquals(idle.getId(), result.items().get(0).courierId());
    }

    @Test
    void deliveryUsesAcceptanceEventNotLastUpdateOrPickupAndDoesNotDuplicateOrders() {
        AppUser customer = user(UserRole.CUSTOMER);
        long id = order(customer, "PICKED_UP", "PAID", NOW.minusSeconds(7200));
        event(id, customer, "ACCEPTED", NOW.minusSeconds(1800));
        event(id, customer, "ACCEPTED", NOW.minusSeconds(1700));
        event(id, customer, "PICKED_UP", NOW.minusSeconds(30));
        long fresh = order(customer, "ACCEPTED", "PAID", NOW.minusSeconds(7200));
        event(fresh, customer, "ACCEPTED", NOW.minusSeconds(1799));
        order(customer, "COMPLETED", "PAID", NOW.minusSeconds(7200));
        try (var time = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            time.when(Instant::now).thenReturn(NOW);
            var result = service.snapshot(AdminRole.OPERATIONS, AdminCommandCenterService.Queue.LATE_DELIVERY, 0, 0);
            assertEquals(2, result.orders().active());
            assertEquals(1, result.orders().lateDelivery());
            assertEquals(id, result.orders().items().get(0).id());
            assertEquals(NOW.minusSeconds(1800), result.orders().items().get(0).stageStart());
        }
    }

    @Test
    void legacyEstimatesAreMarkedAndQueuesHaveStablePagination() {
        AppUser customer = user(UserRole.CUSTOMER);
        long first = order(customer, "PENDING_ASSIGNMENT", "PAID", NOW.minusSeconds(3600));
        for (int i = 0; i < 20; i++) order(customer, "PENDING_ASSIGNMENT", "PAID", NOW.minusSeconds(3600));
        var page = service.snapshot(AdminRole.OPERATIONS, AdminCommandCenterService.Queue.WAITING, 0, 0).orders();
        assertEquals(21, page.totalElements());
        assertEquals(20, page.items().size());
        assertEquals(first, page.items().get(0).id());
        assertTrue(page.items().get(0).estimated());
        var last = service.snapshot(AdminRole.OPERATIONS, AdminCommandCenterService.Queue.WAITING, 99, 0).orders();
        assertEquals(1, last.page());
        assertEquals(1, last.items().size());
        assertNotEquals(first, last.items().get(0).id());
    }

    @Test
    void capacityExcludesOfflineSuspendedAndUnapprovedAndCountsActiveLoad() {
        AppUser idle = onlineCourier();
        AppUser busy = onlineCourier();
        AppUser suspended = onlineCourier();
        jdbc.update("update app_users set enabled = false where id = ?", suspended.getId());
        AppUser unapproved = onlineCourier();
        jdbc.update("update app_users set courier_approved = false where id = ?", unapproved.getId());
        user(UserRole.COURIER);
        long active = order(user(UserRole.CUSTOMER), "ACCEPTED", "PAID", NOW);
        jdbc.update("update campus_orders set courier_id = ? where id = ?", busy.getId(), active);
        var result = service.snapshot(AdminRole.OPERATIONS, AdminCommandCenterService.Queue.ACTIVE, 0, 0).couriers();
        assertEquals(2, result.online());
        assertEquals(1, result.idle());
        assertEquals(idle.getId(), result.items().get(0).id());
        assertEquals(1, result.items().get(1).activeOrders());
    }

    @Test
    void dispatchCandidatesExcludeUnavailableAndRankByLoadThenOnlineTime() {
        AppUser earlyIdle = onlineCourier();
        AppUser lateIdle = onlineCourier();
        AppUser busy = onlineCourier();
        AppUser offline = onlineCourier();
        offline.setCourierAcceptingOrders(false);
        users.saveAndFlush(offline);
        AppUser suspended = onlineCourier();
        jdbc.update("update app_users set enabled = false where id = ?", suspended.getId());
        AppUser unapproved = onlineCourier();
        jdbc.update("update app_users set courier_approved = false where id = ?", unapproved.getId());
        jdbc.update("update app_users set courier_availability_updated_at = ? where id = ?",
                Timestamp.from(NOW.minusSeconds(120)), earlyIdle.getId());
        jdbc.update("update app_users set courier_availability_updated_at = ? where id = ?",
                Timestamp.from(NOW.minusSeconds(60)), lateIdle.getId());
        long active = order(user(UserRole.CUSTOMER), "ACCEPTED", "PAID", NOW);
        jdbc.update("update campus_orders set courier_id = ? where id = ?", busy.getId(), active);
        AppUser historyCustomer = user(UserRole.CUSTOMER);
        for (int i = 0; i < 4; i++) {
            long completed = order(historyCustomer, "COMPLETED", "PAID", NOW.minusSeconds(3600));
            jdbc.update("update campus_orders set courier_id = ?, rating_score = 5 where id = ?",
                    earlyIdle.getId(), completed);
        }
        long waiting = order(user(UserRole.CUSTOMER), "PENDING_ASSIGNMENT", "PAID", NOW);

        var result = dispatchService.candidates(waiting, 0);

        assertEquals(3, result.totalElements());
        assertEquals(earlyIdle.getId(), result.items().get(0).courierId());
        assertTrue(result.items().get(0).recommended());
        assertEquals(4, result.items().get(0).completedOrders());
        assertEquals(4, result.items().get(0).matchedCompletedOrders());
        assertEquals(5.0, result.items().get(0).averageRating());
        assertTrue(result.items().get(0).reasons().stream().anyMatch(reason -> reason.contains("同类订单")));
        assertEquals(lateIdle.getId(), result.items().get(1).courierId());
        assertFalse(result.items().get(1).recommended());
        assertEquals(busy.getId(), result.items().get(2).courierId());
        assertEquals(1, result.items().get(2).activeOrders());
        assertTrue(result.items().get(0).recommendationScore() > result.items().get(2).recommendationScore());
    }

    @Test
    void responseDoesNotExposeQueuesOutsideRolePermissionsAndRejectsInvalidPages() {
        assertNull(service.snapshot(AdminRole.REVIEWER, AdminCommandCenterService.Queue.WAITING, 0, 0).orders());
        assertNull(service.snapshot(AdminRole.SUPPORT, AdminCommandCenterService.Queue.WAITING, 0, 0).couriers());
        assertThrows(ResponseStatusException.class,
                () -> service.snapshot(AdminRole.OPERATIONS, AdminCommandCenterService.Queue.WAITING, -1, 0));
    }

    private AppUser user(UserRole role) {
        return users.saveAndFlush(new AppUser(UUID.randomUUID().toString().replace("-", ""), "hash", "指挥台测试", role));
    }
    private AppUser onlineCourier() {
        AppUser user = user(UserRole.COURIER);
        user.approveCourier();
        user.setCourierAcceptingOrders(true);
        return users.saveAndFlush(user);
    }
    private long order(AppUser customer, String status, String paymentStatus, Instant created) {
        CampusOrder order = orders.saveAndFlush(new CampusOrder("CC-" + UUID.randomUUID().toString().substring(0, 18),
                customer, "测试", "1234", "东门", "宿舍", ServiceType.LOBBY));
        jdbc.update("update campus_orders set status = ?, payment_status = ?, created_at = ? where id = ?",
                status, paymentStatus, Timestamp.from(created), order.getId());
        return order.getId();
    }
    private void payment(long id, AppUser user, Instant paid) {
        jdbc.update("""
                insert into wechat_payments (out_trade_no, business_type, status, amount_cents, user_id,
                    order_id, expires_at, paid_at, created_at, updated_at, version)
                values (?, 'ORDER', 'PAID', 200, ?, ?, now(), ?, now(), now(), 0)
                """, UUID.randomUUID().toString().replace("-", ""), user.getId(), id, Timestamp.from(paid));
    }
    private void event(long id, AppUser user, String status, Instant time) {
        jdbc.update("insert into order_events (order_id, actor_id, status, created_at) values (?, ?, ?, ?)",
                id, user.getId(), status, Timestamp.from(time));
    }
}
