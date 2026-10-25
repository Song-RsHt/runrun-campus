package com.zhangxuan.runruncampus.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhangxuan.runruncampus.auth.Session;
import com.zhangxuan.runruncampus.auth.SessionRepository;
import com.zhangxuan.runruncampus.coupon.UserCoupon;
import com.zhangxuan.runruncampus.coupon.UserCouponRepository;
import com.zhangxuan.runruncampus.membership.MembershipPlanType;
import com.zhangxuan.runruncampus.membership.UserMembership;
import com.zhangxuan.runruncampus.membership.UserMembershipRepository;
import com.zhangxuan.runruncampus.payment.WechatPayment;
import com.zhangxuan.runruncampus.payment.WechatPaymentRepository;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRepository;
import com.zhangxuan.runruncampus.user.UserRole;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.proof-storage.local-directory=target/test-order-proofs")
@ActiveProfiles("test")
@Testcontainers
class OrderConcurrencyPostgresIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private TestRestTemplate http;
    @Autowired private UserRepository users;
    @Autowired private SessionRepository sessions;
    @Autowired private UserCouponRepository coupons;
    @Autowired private UserMembershipRepository memberships;
    @Autowired private OrderRepository orders;
    @Autowired private OrderEventRepository events;
    @Autowired private WechatPaymentRepository payments;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void duplicateSubmissionCreatesOneOrderAndOneEvent() throws Exception {
        Account customer = account(UserRole.CUSTOMER);
        String key = UUID.randomUUID().toString();
        var results = concurrently(16, i -> create(customer, key, request(null, false)));
        assertOneOrder(results, customer);
    }

    @Test
    void duplicateCouponSubmissionConsumesCouponOnce() throws Exception {
        Account customer = account(UserRole.CUSTOMER);
        UserCoupon coupon = coupons.saveAndFlush(new UserCoupon(customer.user(), "并发测试券", 100, 200,
                Instant.now().plusSeconds(3600)));
        String key = UUID.randomUUID().toString();
        var results = concurrently(16, i -> create(customer, key, request(coupon.getId(), false)));
        assertOneOrder(results, customer);
        assertTrue(coupons.findById(coupon.getId()).orElseThrow().isUsed());
        results.forEach(r -> assertEquals(100, r.getBody().path("discountCents").asInt()));
    }

    @Test
    void duplicateMembershipSubmissionConsumesOneTrip() throws Exception {
        Account customer = account(UserRole.CUSTOMER);
        UserMembership membership = membership(customer, 10);
        String key = UUID.randomUUID().toString();
        var results = concurrently(16, i -> create(customer, key, request(null, true)));
        assertOneOrder(results, customer);
        assertEquals(9, memberships.findById(membership.getId()).orElseThrow().getRemainingTrips());
    }

    @Test
    void distinctSubmissionsCannotReuseCoupon() throws Exception {
        Account customer = account(UserRole.CUSTOMER);
        UserCoupon coupon = coupons.saveAndFlush(new UserCoupon(customer.user(), "并发测试券", 100, 200,
                Instant.now().plusSeconds(3600)));
        var results = concurrently(16, i -> create(customer, UUID.randomUUID().toString(), request(coupon.getId(), false)));
        assertStatuses(results, 1, 15);
        assertEquals(1, count("select count(*) from campus_orders where customer_id = ?", customer.user().getId()));
    }

    @Test
    void onlyOneSubmissionCanConsumeLastMembershipTrip() throws Exception {
        Account customer = account(UserRole.CUSTOMER);
        UserMembership membership = membership(customer, 1);
        var results = concurrently(16, i -> create(customer, UUID.randomUUID().toString(), request(null, true)));
        assertStatuses(results, 1, 15);
        assertEquals(0, memberships.findById(membership.getId()).orElseThrow().getRemainingTrips());
        assertEquals(1, count("select count(*) from campus_orders where customer_id = ?", customer.user().getId()));
    }

    @Test
    void cancellingMembershipOrderRestoresTripOnce() {
        Account customer = account(UserRole.CUSTOMER);
        UserMembership membership = membership(customer, 1);
        ResponseEntity<JsonNode> created = create(customer, UUID.randomUUID().toString(), request(null, true));
        long orderId = created.getBody().path("id").asLong();

        assertEquals(200, created.getStatusCode().value());
        assertEquals(0, memberships.findById(membership.getId()).orElseThrow().getRemainingTrips());
        assertEquals(membership.getId(), jdbc.queryForObject(
                "select membership_id from campus_orders where id = ?", Long.class, orderId));

        assertEquals(200, post(customer, "/" + orderId + "/cancel", null, null).getStatusCode().value());
        assertEquals(1, memberships.findById(membership.getId()).orElseThrow().getRemainingTrips());

        assertEquals(409, post(customer, "/" + orderId + "/cancel", null, null).getStatusCode().value());
        assertEquals(1, memberships.findById(membership.getId()).orElseThrow().getRemainingTrips());
    }

    @Test
    void cancellingCouponOrderRestoresCouponOnce() {
        Account customer = account(UserRole.CUSTOMER);
        UserCoupon coupon = coupons.saveAndFlush(new UserCoupon(customer.user(), "取消返还测试券", 100, 200,
                Instant.now().plusSeconds(3600)));
        ResponseEntity<JsonNode> created = create(customer, UUID.randomUUID().toString(), request(coupon.getId(), false));
        long orderId = created.getBody().path("id").asLong();

        assertEquals(200, created.getStatusCode().value());
        assertTrue(coupons.findById(coupon.getId()).orElseThrow().isUsed());
        assertEquals(coupon.getId(), jdbc.queryForObject(
                "select coupon_id from campus_orders where id = ?", Long.class, orderId));

        assertEquals(200, post(customer, "/" + orderId + "/cancel", null, null).getStatusCode().value());
        assertFalse(coupons.findById(coupon.getId()).orElseThrow().isUsed());

        assertEquals(409, post(customer, "/" + orderId + "/cancel", null, null).getStatusCode().value());
        assertFalse(coupons.findById(coupon.getId()).orElseThrow().isUsed());
    }

    @Test
    void upgradeLinksHistoricalOrdersAndRestoresOnlyCancelledTrips() {
        String schema = "membership_refund_upgrade";
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).target("18").load().migrate();
        jdbc.update("insert into " + schema + ".app_users "
                + "(id, created_at, display_name, password_hash, phone, role) values "
                + "(1, now() - interval '2 days', 'QA Customer', 'unused', '19904009101', 'CUSTOMER')");
        jdbc.update("insert into " + schema + ".user_memberships "
                + "(id, expires_at, plan, price_cents, purchased_at, remaining_trips, total_trips, user_id) values "
                + "(11, now() + interval '28 days', 'LIGHT', 990, now() - interval '1 day', 8, 10, 1)");
        jdbc.update("insert into " + schema + ".campus_orders "
                + "(id, created_at, destination, fee_cents, merchant, order_no, pickup_code, pickup_location, "
                + "service_type, status, updated_at, customer_id, membership_used, membership_plan_title, "
                + "delivery_code) values "
                + "(21, now() - interval '12 hours', '测试楼', 0, '测试驿站', 'HIST-CANCELLED', '1234', "
                + "'测试驿站', 'LOBBY', 'CANCELLED', now(), 1, true, '轻享月卡', '123456'), "
                + "(22, now() - interval '11 hours', '测试楼', 0, '测试驿站', 'HIST-COMPLETED', '5678', "
                + "'测试驿站', 'LOBBY', 'COMPLETED', now(), 1, true, '轻享月卡', '654321')");

        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).load().migrate();

        assertEquals(List.of(11L, 11L), jdbc.queryForList(
                "select membership_id from " + schema + ".campus_orders order by id", Long.class));
        assertEquals(9, jdbc.queryForObject(
                "select remaining_trips from " + schema + ".user_memberships where id = 11", Integer.class));
    }

    @Test
    void upgradeLinksHistoricalCouponOrdersAndRestoresOnlyCancelledCoupon() {
        String schema = "coupon_refund_upgrade";
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).target("19").load().migrate();
        jdbc.update("insert into " + schema + ".app_users "
                + "(id, created_at, display_name, password_hash, phone, role) values "
                + "(1, now() - interval '2 days', 'QA Customer', 'unused', '19904009102', 'CUSTOMER')");
        jdbc.update("insert into " + schema + ".user_coupons "
                + "(id, created_at, discount_cents, expires_at, minimum_order_cents, title, used_at, user_id) values "
                + "(11, now() - interval '2 days', 100, now() + interval '28 days', 200, '取消券', "
                + "now() - interval '13 hours', 1), "
                + "(12, now() - interval '2 days', 100, now() + interval '28 days', 200, '完成券', "
                + "now() - interval '12 hours', 1)");
        jdbc.update("insert into " + schema + ".campus_orders "
                + "(id, created_at, destination, fee_cents, merchant, order_no, pickup_code, pickup_location, "
                + "service_type, status, updated_at, customer_id, coupon_title, discount_cents, delivery_code) values "
                + "(21, now() - interval '12 hours', '测试楼', 100, '测试驿站', 'COUPON-CANCELLED', '1234', "
                + "'测试驿站', 'LOBBY', 'CANCELLED', now(), 1, '取消券', 100, '123456'), "
                + "(22, now() - interval '11 hours', '测试楼', 100, '测试驿站', 'COUPON-COMPLETED', '5678', "
                + "'测试驿站', 'LOBBY', 'COMPLETED', now(), 1, '完成券', 100, '654321')");

        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).load().migrate();

        assertEquals(List.of(11L, 12L), jdbc.queryForList(
                "select coupon_id from " + schema + ".campus_orders order by id", Long.class));
        assertNull(jdbc.queryForObject(
                "select used_at from " + schema + ".user_coupons where id = 11", Instant.class));
        assertNotNull(jdbc.queryForObject(
                "select used_at from " + schema + ".user_coupons where id = 12", Instant.class));
    }

    @Test
    void upgradeMarksHistoricalOrdersPaidWithoutCreatingWechatTransactions() {
        String schema = "wechat_payment_upgrade";
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).target("20").load().migrate();
        jdbc.update("insert into " + schema + ".app_users "
                + "(id, created_at, display_name, password_hash, phone, role) values "
                + "(1, now() - interval '2 days', 'QA Customer', 'unused', '19904009103', 'CUSTOMER')");
        jdbc.update("insert into " + schema + ".campus_orders "
                + "(id, created_at, destination, fee_cents, merchant, order_no, pickup_code, pickup_location, "
                + "service_type, status, updated_at, customer_id, delivery_code) values "
                + "(21, now() - interval '1 day', '测试楼', 300, '测试商家', 'PAY-HISTORICAL', '1234', "
                + "'东门', 'DOOR', 'COMPLETED', now(), 1, '123456')");

        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).load().migrate();

        assertEquals("PAID", jdbc.queryForObject(
                "select payment_status from " + schema + ".campus_orders where id = 21", String.class));
        assertEquals(300, jdbc.queryForObject(
                "select paid_cents from " + schema + ".campus_orders where id = 21", Integer.class));
        assertEquals(0, countInSchema(schema, "wechat_payments"));
    }

    @Test
    void concurrentIndependentOrdersHaveUniqueNumbers() throws Exception {
        List<Account> customers = new ArrayList<>();
        for (int i = 0; i < 16; i++) customers.add(account(UserRole.CUSTOMER));
        List<ResponseEntity<JsonNode>> results = new ArrayList<>();
        for (int batch = 0; batch < 6; batch++) {
            results.addAll(concurrently(16, i -> create(customers.get(i), null, request(null, false))));
        }
        assertStatuses(results, 96, 0);
        assertEquals(96, results.stream().map(r -> r.getBody().path("orderNo").asText()).distinct().count());
        for (Account customer : customers) {
            assertEquals(6, count("select count(*) from campus_orders where customer_id = ?", customer.user().getId()));
        }
    }

    @Test
    void onlyOneCourierWinsAndCreatesOneEventAndMessage() throws Exception {
        Account customer = account(UserRole.CUSTOMER);
        long id = pendingOrder(customer);
        List<Account> couriers = new ArrayList<>();
        for (int i = 0; i < 16; i++) couriers.add(account(UserRole.COURIER));
        var results = concurrently(16, i -> post(couriers.get(i), "/" + id + "/accept", null, null));
        assertStatuses(results, 1, 15);
        int winner = -1;
        for (int i = 0; i < results.size(); i++) if (results.get(i).getStatusCode().value() == 200) winner = i;
        assertEquals(couriers.get(winner).user().getId(),
                jdbc.queryForObject("select courier_id from campus_orders where id = ?", Long.class, id));
        assertEquals(1, count("select count(*) from order_events where order_id = ? and status = 'ACCEPTED'", id));
        assertEquals(1, count("select count(*) from order_messages where order_id = ? and type = 'ORDER_ACCEPTED'", id));
    }

    @Test
    void courierBusinessStatsComeFromCompletedEventsAndActualOrderFee() {
        Account customer = account(UserRole.CUSTOMER);
        Account courier = account(UserRole.COURIER);
        long id = pendingOrder(customer);
        int feeCents = jdbc.queryForObject(
                "select fee_cents from campus_orders where id = ?", Integer.class, id);
        jdbc.update("update campus_orders set courier_id = ?, status = 'COMPLETED', updated_at = now() where id = ?",
                courier.user().getId(), id);
        jdbc.update("insert into order_events (created_at, status, order_id, actor_id) values (now(), 'COMPLETED', ?, ?)",
                id, courier.user().getId());

        OrderRepository.CourierBusinessStats stats = orders.businessStatsForCourier(
                courier.user(), Instant.now().minusSeconds(60));

        assertEquals(1L, stats.getTodayCompletedOrders());
        assertEquals(1L, stats.getTotalCompletedOrders());
        assertEquals((long) feeCents, stats.getTodayGrossServiceFeeCents());
        assertEquals((long) feeCents, stats.getTotalGrossServiceFeeCents());
    }

    @Test
    void cancellationAndAcceptanceCannotBothWin() throws Exception {
        Account customer = account(UserRole.CUSTOMER);
        Account courier = account(UserRole.COURIER);
        long id = pendingOrder(customer);
        var results = concurrently(2, i -> post(i == 0 ? customer : courier,
                "/" + id + (i == 0 ? "/cancel" : "/accept"), null, null));
        assertStatuses(results, 1, 1);
        String status = jdbc.queryForObject("select status from campus_orders where id = ?", String.class, id);
        assertEquals(results.get(0).getStatusCode().value() == 200 ? "CANCELLED" : "ACCEPTED", status);
        assertEquals(2, count("select count(*) from order_events where order_id = ?", id));
        assertEquals(status.equals("ACCEPTED") ? 1 : 0, count("select count(*) from order_messages where order_id = ?", id));
    }

    @Test
    void manualDispatchAndCourierAcceptanceCannotBothWin() throws Exception {
        Account customer = account(UserRole.CUSTOMER);
        Account administrator = account(UserRole.ADMIN);
        Account dispatchedCourier = account(UserRole.COURIER);
        dispatchedCourier.user().approveCourier();
        dispatchedCourier.user().setCourierAcceptingOrders(true);
        users.saveAndFlush(dispatchedCourier.user());
        Account acceptingCourier = account(UserRole.COURIER);
        long id = pendingOrder(customer);

        var results = concurrently(2, i -> i == 0
                ? adminPost(administrator, "/" + id + "/dispatch", new AdminDispatchRequest(dispatchedCourier.user().getId()))
                : post(acceptingCourier, "/" + id + "/accept", null, null));

        assertStatuses(results, 1, 1);
        boolean adminWon = results.get(0).getStatusCode().value() == 200;
        assertEquals(adminWon ? dispatchedCourier.user().getId() : acceptingCourier.user().getId(),
                jdbc.queryForObject("select courier_id from campus_orders where id = ?", Long.class, id));
        assertEquals(1, count("select count(*) from order_events where order_id = ? and status = 'ACCEPTED'", id));
        assertEquals(adminWon ? 2 : 1,
                count("select count(*) from order_messages where order_id = ? and type = 'ORDER_ACCEPTED'", id));
        assertEquals(adminWon ? 1 : 0,
                count("select count(*) from admin_audit_logs where target_id = ? and action = 'ORDER_DISPATCHED'", id));
    }

    @Test
    void concurrentKeyReuseWithDifferentContentReturnsConflict() throws Exception {
        Account customer = account(UserRole.CUSTOMER);
        String key = UUID.randomUUID().toString();
        var changed = new OrderController.CreateOrderRequest("并发验收", "1234", "测试驿站", "另一栋楼",
                ServiceType.PACKAGE_MEDIUM, null, false);
        var results = concurrently(16, i -> create(customer, key, i % 2 == 0 ? request(null, false) : changed));
        assertStatuses(results, 8, 8);
        assertEquals(1, count("select count(*) from campus_orders where customer_id = ?", customer.user().getId()));
    }

    @Test
    void requestKeysAreScopedToCustomer() throws Exception {
        Account first = account(UserRole.CUSTOMER);
        Account second = account(UserRole.CUSTOMER);
        String key = UUID.randomUUID().toString();
        var results = concurrently(16, i -> create(i % 2 == 0 ? first : second, key, request(null, false)));
        assertStatuses(results, 16, 0);
        assertEquals(2, results.stream().map(r -> r.getBody().path("id").asLong()).distinct().count());
        assertEquals(1, count("select count(*) from campus_orders where customer_id = ?", first.user().getId()));
        assertEquals(1, count("select count(*) from campus_orders where customer_id = ?", second.user().getId()));
    }

    @Test
    void retryAfterCancellationReturnsExistingOrderAndNewKeyCreatesNewOrder() {
        Account customer = account(UserRole.CUSTOMER);
        String key = UUID.randomUUID().toString();
        var original = create(customer, key, request(null, false));
        assertEquals(200, original.getStatusCode().value());
        long id = original.getBody().path("id").asLong();
        assertEquals(200, post(customer, "/" + id + "/cancel", null, null).getStatusCode().value());
        var retry = create(customer, key, request(null, false));
        assertEquals(200, retry.getStatusCode().value());
        assertEquals(id, retry.getBody().path("id").asLong());
        assertEquals("CANCELLED", retry.getBody().path("status").asText());
        var next = create(customer, UUID.randomUUID().toString(), request(null, false));
        assertEquals(200, next.getStatusCode().value());
        assertNotEquals(id, next.getBody().path("id").asLong());
        assertEquals(2, count("select count(*) from campus_orders where customer_id = ?", customer.user().getId()));
    }

    @Test
    void rejectsMalformedKeyAndAllowsRetryAfterInvalidBody() {
        Account customer = account(UserRole.CUSTOMER);
        assertEquals(400, create(customer, "invalid-key", request(null, false)).getStatusCode().value());
        String key = UUID.randomUUID().toString();
        var invalid = new OrderController.CreateOrderRequest("", "1234", "测试驿站", "测试楼",
                ServiceType.PACKAGE_MEDIUM, null, false);
        assertEquals(400, create(customer, key, invalid).getStatusCode().value());
        assertEquals(0, count("select count(*) from campus_orders where customer_id = ?", customer.user().getId()));
        assertEquals(200, create(customer, key, request(null, false)).getStatusCode().value());
    }

    @Test
    void eventFailureRollsBackOrderCouponAndKeySoRetryCanSucceed() {
        Account customer = account(UserRole.CUSTOMER);
        UserCoupon coupon = coupons.saveAndFlush(new UserCoupon(customer.user(), "回滚测试券", 100, 200,
                Instant.now().plusSeconds(3600)));
        String key = UUID.randomUUID().toString();
        failEventWrite(customer, key, request(coupon.getId(), false));
        assertFalse(coupons.findById(coupon.getId()).orElseThrow().isUsed());
        assertEquals(200, create(customer, key, request(coupon.getId(), false)).getStatusCode().value());
        assertTrue(coupons.findById(coupon.getId()).orElseThrow().isUsed());
    }

    @Test
    void eventFailureRollsBackMembershipTripSoRetryCanSucceed() {
        Account customer = account(UserRole.CUSTOMER);
        UserMembership membership = membership(customer, 1);
        String key = UUID.randomUUID().toString();
        failEventWrite(customer, key, request(null, true));
        assertEquals(1, memberships.findById(membership.getId()).orElseThrow().getRemainingTrips());
        assertEquals(200, create(customer, key, request(null, true)).getStatusCode().value());
        assertEquals(0, memberships.findById(membership.getId()).orElseThrow().getRemainingTrips());
    }

    private void failEventWrite(Account customer, String key, OrderController.CreateOrderRequest request) {
        jdbc.execute("alter table order_events add constraint test_reject_event check (actor_id <> "
                + customer.user().getId() + ")");
        try {
            assertEquals(500, create(customer, key, request).getStatusCode().value());
        } finally {
            jdbc.execute("alter table order_events drop constraint test_reject_event");
        }
        assertEquals(0, count("select count(*) from campus_orders where customer_id = ?", customer.user().getId()));
        assertEquals(0, count("select count(*) from order_events where actor_id = ?", customer.user().getId()));
    }

    private void assertOneOrder(List<ResponseEntity<JsonNode>> results, Account customer) {
        assertStatuses(results, results.size(), 0);
        assertEquals(1, results.stream().map(r -> r.getBody().path("id").asLong()).distinct().count());
        long id = results.get(0).getBody().path("id").asLong();
        assertEquals(1, count("select count(*) from campus_orders where customer_id = ?", customer.user().getId()));
        assertEquals(1, count("select count(*) from order_events where order_id = ?", id));
    }

    private void assertStatuses(List<ResponseEntity<JsonNode>> results, long successes, long conflicts) {
        List<Integer> statuses = results.stream().map(r -> r.getStatusCode().value()).toList();
        assertEquals(successes, statuses.stream().filter(s -> s == 200).count(), statuses.toString());
        assertEquals(conflicts, statuses.stream().filter(s -> s == 409).count(), statuses.toString());
        assertEquals(successes + conflicts, results.size());
    }

    private long count(String sql, Long id) { return jdbc.queryForObject(sql, Long.class, id); }
    private long countInSchema(String schema, String table) {
        return jdbc.queryForObject("select count(*) from " + schema + "." + table, Long.class);
    }

    private Account account(UserRole role) {
        AppUser user = users.saveAndFlush(new AppUser(UUID.randomUUID().toString().replace("-", ""),
                "unused-test-hash", "并发验收", role));
        String token = UUID.randomUUID().toString();
        Session session = new Session(token, user, Instant.now().plusSeconds(3600));
        if (role == UserRole.ADMIN) {
            org.springframework.test.util.ReflectionTestUtils.setField(session, "adminMfaVerified", true);
        }
        sessions.saveAndFlush(session);
        return new Account(user, token);
    }

    private UserMembership membership(Account customer, int remaining) {
        UserMembership membership = new UserMembership(customer.user(), MembershipPlanType.LIGHT,
                Instant.now().plusSeconds(3600));
        while (membership.getRemainingTrips() > remaining) membership.consumeTrip();
        return memberships.saveAndFlush(membership);
    }

    private long pendingOrder(Account customer) {
        CampusOrder order = orders.saveAndFlush(new CampusOrder("TEST-" + UUID.randomUUID().toString().substring(0, 18),
                customer.user(), "并发验收", "1234", "测试驿站", "测试楼", ServiceType.PACKAGE_MEDIUM));
        WechatPayment payment = WechatPayment.forOrder(customer.user(), order, order.getFeeCents(),
                Instant.now().plusSeconds(900));
        payment.markPaid("WX-TEST-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        payments.saveAndFlush(payment);
        events.saveAndFlush(new OrderEvent(order, customer.user(), order.getStatus()));
        return order.getId();
    }

    private OrderController.CreateOrderRequest request(Long couponId, boolean membership) {
        return new OrderController.CreateOrderRequest("并发验收", "1234", "测试驿站", "测试楼",
                ServiceType.PACKAGE_MEDIUM, couponId, membership);
    }

    private ResponseEntity<JsonNode> create(Account customer, String key, OrderController.CreateOrderRequest request) {
        return post(customer, "", key, request);
    }

    private ResponseEntity<JsonNode> post(Account account, String path, String key, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(account.token());
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (key != null) headers.set("Idempotency-Key", key);
        return http.exchange("/api/v1/orders" + path, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private ResponseEntity<JsonNode> adminPost(Account account, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(account.token());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange("/api/v1/admin/orders" + path, HttpMethod.POST,
                new HttpEntity<>(body, headers), JsonNode.class);
    }

    private List<ResponseEntity<JsonNode>> concurrently(int workers, IntFunction<ResponseEntity<JsonNode>> action)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ResponseEntity<JsonNode>>> futures = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new TimeoutException("Start barrier timed out");
                    return action.apply(index);
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<ResponseEntity<JsonNode>> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(35, TimeUnit.SECONDS));
        }
    }

    private record Account(AppUser user, String token) { }
    private record AdminDispatchRequest(Long courierId) { }
}
