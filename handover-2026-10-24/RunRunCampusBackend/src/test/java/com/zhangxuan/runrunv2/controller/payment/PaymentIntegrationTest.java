package com.zhangxuan.runrunv2.controller.payment;

import com.zhangxuan.runrunv2.IdentityApplication;
import com.zhangxuan.runrunv2.config.OrderJson;
import com.zhangxuan.runrunv2.dto.CancelRequest;
import com.zhangxuan.runrunv2.dto.CreateOrderRequest;
import com.zhangxuan.runrunv2.dto.PaymentResultRequest;
import com.zhangxuan.runrunv2.dto.PaymentVersionRequest;
import com.zhangxuan.runrunv2.enums.Result;
import com.zhangxuan.runrunv2.enums.ServiceType;
import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.integration.payment.MockPaymentAdapter;
import com.zhangxuan.runrunv2.integration.wechat.WechatGateway;
import com.zhangxuan.runrunv2.security.IdentitySecrets;
import com.zhangxuan.runrunv2.service.OrderService;
import com.zhangxuan.runrunv2.service.PaymentService;
import com.zhangxuan.runrunv2.vo.AdjustmentFundsVO;
import com.zhangxuan.runrunv2.vo.CancelledOrderVO;
import com.zhangxuan.runrunv2.vo.CreatedOrderVO;
import com.zhangxuan.runrunv2.vo.PaymentAttemptVO;
import com.zhangxuan.runrunv2.vo.PaymentSummaryVO;
import com.zhangxuan.runrunv2.vo.RefundOrderVO;
import com.zhangxuan.runrunv2.vo.StartedPaymentVO;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;



















































































































































































@SpringBootTest(
    classes = IdentityApplication.class,
    properties = {
      "spring.config.name=application-v2-test",
      "rrc.identity.environment=test",
      "rrc.identity.app-id=synthetic-app",
      "rrc.identity.app-secret=synthetic-secret",
      "rrc.payment.mock-enabled=true",
      "rrc.payment.expiry-delay-ms=86400000"
    })
@AutoConfigureMockMvc(
    print = MockMvcPrint.NONE)
// Close scheduled workers and JDBC pools with the class-scoped database fixture.
@DirtiesContext(
    classMode = ClassMode.AFTER_CLASS)
@Testcontainers
class PaymentIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) throws Exception {
    try (var conn =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var sql = conn.createStatement()) {
      for (String name :
          List.of(
              "rrc_0001__create_v2_schema.sql",
              "rrc_0002__create_identity_foundation.sql",
              "rrc_0003__identity_runtime.sql",
              "rrc_0100__orders_and_service_config.sql",
              "rrc_0200__mock_payments.sql"))
        sql.execute(Files.readString(Path.of("../../server/db/migrations/up", name)));
    }
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add(
        "rrc.identity.encryption-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
    registry.add(
        "rrc.identity.hash-key",
        () ->
            Base64.getEncoder()
                .encodeToString(
                    "synthetic-different-hash-key-000001"
                        .getBytes(StandardCharsets.UTF_8)));
  }

  @Autowired JdbcTemplate db;

  @Autowired PaymentService payments;

  @Autowired MockPaymentAdapter adapter;

  @Autowired OrderService orders;

  @Autowired IdentitySecrets secrets;

  @Autowired PlatformTransactionManager transactions;

  @Autowired MockMvc http;

  @Autowired OrderJson json;

  @MockitoBean Clock clock;

  @MockitoBean WechatGateway wechat;

  String user, other, admin, courier;

  int seq;

  @BeforeEach
  void setup() {
    db.execute(
        "TRUNCATE"
            + " runrun_v2.app_users,runrun_v2.service_points,runrun_v2.delivery_zones,runrun_v2.platform_settings"
            + " RESTART IDENTITY CASCADE");
    // Simulator event tables deliberately have no identity FK; clear only synthetic fixtures.
    db.execute("TRUNCATE runrun_v2.mock_channel_results,runrun_v2.payment_result_events");
    user = account("13800000001", "USER");
    other = account("13800000002", "USER");
    admin = account("13800000003", "ADMIN");
    courier = account("13800000004", "COURIER");
    seq = 0;
    long aid =
        db.queryForObject("SELECT id FROM runrun_v2.app_users WHERE role='ADMIN'", Long.class);
    db.update(
        "INSERT INTO runrun_v2.platform_settings(id,new_order_enabled,review_owner_id)"
            + " VALUES(1,true,?)",
        aid);
    db.update(
        "INSERT INTO runrun_v2.delivery_zones(code,name,package_surcharge_cents)"
            + " VALUES('STANDARD','合成区域',0)");
    db.update(
        "INSERT INTO runrun_v2.service_points(code,name,latitude,longitude)"
            + " VALUES('TEST','合成点',30,120)");
    now("2026-10-27T01:00:00Z");
  }

  String account(String phone, String role) {
    long id =
        db.queryForObject(
            "INSERT INTO runrun_v2.app_users(phone,role,v2_admin_enabled) VALUES(?,?,?) RETURNING"
                + " id",
            Long.class,
            phone,
            role,
            role.equals("ADMIN"));
    String token = secrets.token();
    db.update(
        "INSERT INTO runrun_v2.user_sessions(user_id,token_hash,expires_at)"
            + " VALUES(?,?,now()+interval '1 day')",
        id,
        secrets.hash("session", token));
    return token;
  }

  void now(String instant) {
    when(clock.instant()).thenReturn(Instant.parse(instant));
  }

  String key() {
    return "synthetic-" + (++seq);
  }

  CreatedOrderVO create() {
    return orders.create(
        user, key(), new CreateOrderRequest(ServiceType.PACKAGE_XL, "STANDARD", "TEST", "合成目的地", 0L));
  }

  StartedPaymentVO start(String id) {
    return payments.start(
        user, id, key(), new PaymentVersionRequest(payments.get(user, id).payment().version()));
  }

  PaymentAttemptVO result(String id, Result result) {
    long version =
        db.queryForObject(
            "SELECT version FROM runrun_v2.payment_attempts WHERE id=?",
            Long.class,
            Long.valueOf(id));
    return payments.paymentResult(
        admin, id, key(), new PaymentResultRequest(version, result, "event-" + key()));
  }

  RefundOrderVO resultRefund(String id, Result result) {
    long version =
        db.queryForObject(
            "SELECT version FROM runrun_v2.refund_orders WHERE id=?", Long.class, Long.valueOf(id));
    return payments.refundResult(
        admin, id, key(), new PaymentResultRequest(version, result, "event-" + key()));
  }

  CreatedOrderVO paid() {
    var c = create();
    result(start(c.originalPayment().id()).attempt().id(), Result.SUCCESS);
    return c;
  }

  PaymentSummaryVO summary(CreatedOrderVO c) {
    return payments.get(user, c.originalPayment().id());
  }

  CancelledOrderVO cancel(CreatedOrderVO c) {
    return payments.cancel(
        user,
        c.order().id(),
        key(),
        new CancelRequest(orders.get(user, c.order().id()).version(), "合成取消原因"));
  }

  AdjustmentFundsVO approve(CreatedOrderVO c, String adjustment, long delta) {
    return new TransactionTemplate(transactions)
        .execute(
            s ->
                payments.approvedAdjustment(
                    admin,
                    c.order().id(),
                    adjustment,
                    delta,
                    orders.get(user, c.order().id()).version(),
                    "合成已批准规格差额",
                    key()));
  }

  void failure(String code, Executable action) {
    assertEquals(code, assertThrows(IdentityFailure.class, action).code);
  }

  long count(String table) {
    return db.queryForObject("SELECT count(*) FROM runrun_v2." + table, Long.class);
  }

  @Test
  void actualOrderWriterAndSuccessAreAtomicAndPrivate() throws Exception {
    var c = create();
    assertEquals(1, count("payment_orders"));
    assertEquals("UNPAID", summary(c).payment().status());
    var a = start(c.originalPayment().id());
    assertEquals("PENDING_PAYMENT", orders.get(user, c.order().id()).status());
    result(a.attempt().id(), Result.PROCESSING);
    result(a.attempt().id(), Result.SUCCESS);
    assertEquals("PENDING_ASSIGNMENT", orders.get(user, c.order().id()).status());
    assertEquals("PAID", summary(c).payment().status());
    assertEquals(1, count("payment_financial_events"));
    assertEquals(
        240L,
        db.queryForObject(
            "SELECT platform_cents FROM runrun_v2.payment_financial_events", Long.class));
    http.perform(
            get("/api/v2/payments/" + c.originalPayment().id())
                .header("Authorization", "Bearer " + user))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.payment.id").isString())
        .andExpect(jsonPath("$.attempts[0].channelReference").doesNotExist());
    failure("RESOURCE_NOT_FOUND", () -> payments.get(other, c.originalPayment().id()));
    http.perform(
            get("/api/v2/payments/" + c.originalPayment().id())
                .header("Authorization", "Bearer " + courier))
        .andExpect(status().isForbidden());
  }

  @Test
  void failureCanRetryButDeadlineNeverMoves() {
    var c = create();
    var first = start(c.originalPayment().id());
    result(first.attempt().id(), Result.FAILED);
    now("2026-10-27T01:29:59Z");
    var second = start(c.originalPayment().id());
    assertEquals(2, second.attempt().attemptNo());
    assertEquals(c.originalPayment().deadline(), second.payment().deadline());
    now("2026-10-27T01:30:00Z");
    failure("PAYMENT_CLOSED", () -> start(c.originalPayment().id()));
    assertEquals(1, payments.closeExpired());
    assertEquals(0, payments.closeExpired());
    assertEquals("CANCELLED", orders.get(user, c.order().id()).status());
    assertEquals(0, count("refund_orders"));
  }

  @Test
  void concurrentAttemptRequestsProduceOnlyOneEffectivePayment() throws Exception {
    var c = create();
    Callable<StartedPaymentVO> start =
        () ->
            payments.start(
                user, c.originalPayment().id(), "same-concurrent-key", new PaymentVersionRequest(0L));
    race(start, start);
    assertEquals(1, count("payment_attempts"));
    failure(
        "PAYMENT_RESULT_PENDING",
        () -> payments.start(user, c.originalPayment().id(), key(), new PaymentVersionRequest(1L)));
  }

  @Test
  void failedQueryAllowsRetryAndOldFailureCannotDowngradeNewAttempt() {
    var c = create();
    var first = start(c.originalPayment().id());
    new TransactionTemplate(transactions)
        .executeWithoutResult(s -> adapter.record("PAYMENT", first.attempt().id(), Result.FAILED));
    payments.queryPayment(
        user, c.originalPayment().id(), key(), new PaymentVersionRequest(summary(c).payment().version()));
    var second = start(c.originalPayment().id());
    result(first.attempt().id(), Result.FAILED);
    assertEquals("PROCESSING", summary(c).payment().status());
    result(first.attempt().id(), Result.SUCCESS);
    result(second.attempt().id(), Result.SUCCESS);
    assertEquals(1, count("payment_financial_events"));
    assertEquals(1, count("refund_orders"));
  }

  @Test
  void timeoutAndUnknownRequireQueryAndDoNotCreateSecondAttempt() {
    var c = create();
    var a = start(c.originalPayment().id());
    result(a.attempt().id(), Result.UNKNOWN);
    failure("PAYMENT_RESULT_PENDING", () -> start(c.originalPayment().id()));
    var pending =
        payments.queryPayment(
            user,
            c.originalPayment().id(),
            key(),
            new PaymentVersionRequest(summary(c).payment().version()));
    assertEquals("UNKNOWN", pending.payment().status());
    assertEquals(1, count("payment_attempts"));
    // Channel has received success but the callback was lost, then the process restarts.
    new TransactionTemplate(transactions)
        .executeWithoutResult(s -> adapter.record("PAYMENT", a.attempt().id(), Result.SUCCESS));
    var reconciled =
        payments.queryPayment(
            user, c.originalPayment().id(), key(), new PaymentVersionRequest(pending.payment().version()));
    assertEquals("PAID", reconciled.payment().status());
    assertEquals(1, count("payment_financial_events"));
    assertEquals("PENDING_ASSIGNMENT", orders.get(user, c.order().id()).status());
  }

  @Test
  void duplicateRequestEventAndRegressiveResultsNeverDoubleBook() {
    var c = create();
    String id = c.originalPayment().id();
    var request = new PaymentVersionRequest(0L);
    StartedPaymentVO first = payments.start(user, id, "attempt-key", request);
    assertEquals(first, payments.start(user, id, "attempt-key", request));
    failure(
        "IDEMPOTENCY_KEY_CONFLICT",
        () -> payments.start(user, id, "attempt-key", new PaymentVersionRequest(1L)));
    var body = new PaymentResultRequest(0L, Result.SUCCESS, "same-event");
    var result = payments.paymentResult(admin, first.attempt().id(), "callback-key", body);
    assertEquals(result, payments.paymentResult(admin, first.attempt().id(), "callback-key", body));
    assertEquals(
        result, payments.paymentResult(admin, first.attempt().id(), "duplicate-event-key", body));
    result(first.attempt().id(), Result.FAILED);
    result(first.attempt().id(), Result.UNKNOWN);
    assertEquals(1, count("payment_financial_events"));
    assertEquals("PAID", summary(c).payment().status());
    failure(
        "IDEMPOTENCY_KEY_CONFLICT",
        () ->
            payments.paymentResult(
                admin,
                first.attempt().id(),
                key(),
                new PaymentResultRequest(0L, Result.FAILED, "same-event")));
  }

  @Test
  void globalEventIdCannotBeReusedAcrossOrdersOrAdministrators() throws Exception {
    var first = create();
    var second = create();
    var a = start(first.originalPayment().id());
    var b = start(second.originalPayment().id());
    String otherAdmin = account("13800000005", "ADMIN");
    Callable<Object> one =
        () -> {
          try {
            return payments.paymentResult(
                admin,
                a.attempt().id(),
                "global-one",
                new PaymentResultRequest(0L, Result.SUCCESS, "global-shared-event"));
          } catch (IdentityFailure failure) {
            assertEquals("IDEMPOTENCY_KEY_CONFLICT", failure.code);
            return null;
          }
        };
    Callable<Object> two =
        () -> {
          try {
            return payments.paymentResult(
                otherAdmin,
                b.attempt().id(),
                "global-two",
                new PaymentResultRequest(0L, Result.SUCCESS, "global-shared-event"));
          } catch (IdentityFailure failure) {
            assertEquals("IDEMPOTENCY_KEY_CONFLICT", failure.code);
            return null;
          }
        };
    race(one, two);
    assertEquals(1, count("payment_financial_events"));
    assertEquals(
        1L,
        db.queryForObject(
            "SELECT count(*) FROM runrun_v2.payment_orders WHERE status='PAID'", Long.class));
  }

  @Test
  void lateSuccessAtExactDeadlineRefundsWithoutRevival() {
    var c = create();
    var a = start(c.originalPayment().id());
    now("2026-10-27T01:30:00Z");
    result(a.attempt().id(), Result.SUCCESS);
    result(a.attempt().id(), Result.SUCCESS);
    assertEquals("CANCELLED", orders.get(user, c.order().id()).status());
    assertEquals("CLOSED", summary(c).payment().status());
    assertEquals(1, count("refund_orders"));
    assertEquals("LATE_PAYMENT", summary(c).refunds().get(0).purpose());
    assertEquals(0, count("payment_financial_events"));
    resultRefund(summary(c).refunds().get(0).id(), Result.SUCCESS);
    assertEquals("CANCELLED", orders.get(user, c.order().id()).status());
  }

  @Test
  void expiryThenLostLateCallbackCanBeQueried() {
    var c = create();
    var a = start(c.originalPayment().id());
    now("2026-10-27T01:31:00Z");
    payments.closeExpired();
    new TransactionTemplate(transactions)
        .executeWithoutResult(s -> adapter.record("PAYMENT", a.attempt().id(), Result.SUCCESS));
    var reconciled =
        payments.queryPayment(
            user,
            c.originalPayment().id(),
            key(),
            new PaymentVersionRequest(summary(c).payment().version()));
    assertEquals("CLOSED", reconciled.payment().status());
    assertEquals(1, reconciled.refunds().size());
  }

  @Test
  void failedOldAttemptLateSuccessAndNewSuccessRefundExtraReceipt() {
    var c = create();
    var first = start(c.originalPayment().id());
    result(first.attempt().id(), Result.FAILED);
    var second = start(c.originalPayment().id());
    result(second.attempt().id(), Result.SUCCESS);
    result(first.attempt().id(), Result.SUCCESS);
    assertEquals(1, count("payment_financial_events"));
    assertEquals(1, count("refund_orders"));
    assertEquals("EXTRA_RECEIPT", summary(c).refunds().get(0).purpose());
  }

  @Test
  void unpaidCancelCreatesNoRefundAndLateReceiptGetsCompensated() {
    var c = create();
    var a = start(c.originalPayment().id());
    var cancelled = cancel(c);
    assertEquals("CANCELLED", cancelled.order().status());
    assertTrue(cancelled.refunds().isEmpty());
    result(a.attempt().id(), Result.SUCCESS);
    assertEquals(1, count("refund_orders"));
  }

  @Test
  void paidCancellationRefundStagesAndQueryNeverPretendAcceptanceIsReceipt() {
    var c = paid();
    var cancelled = cancel(c);
    RefundOrderVO r = cancelled.refunds().get(0);
    assertEquals("REQUESTED", r.status());
    assertEquals(800, r.amountCents());
    r = resultRefund(r.id(), Result.PROCESSING);
    assertNull(r.succeededAt());
    r = resultRefund(r.id(), Result.UNKNOWN);
    assertNull(r.succeededAt());
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            s -> adapter.record("REFUND", cancelled.refunds().get(0).id(), Result.SUCCESS));
    r = payments.queryRefund(user, r.id(), key(), new PaymentVersionRequest(r.version()));
    assertEquals("SUCCEEDED", r.status());
    assertNotNull(r.succeededAt());
    resultRefund(r.id(), Result.SUCCESS);
    resultRefund(r.id(), Result.FAILED);
    assertEquals(2, count("payment_financial_events"));
  }

  @Test
  void acceptedCanCancelButPickedUpCannot() {
    var c = paid();
    db.update(
        "UPDATE runrun_v2.v2_orders SET status='ACCEPTED',version=version+1 WHERE id=?",
        Long.valueOf(c.order().id()));
    assertEquals(1, cancel(c).refunds().size());
    var picked = paid();
    db.update(
        "UPDATE runrun_v2.v2_orders SET status='PICKED_UP',version=version+1 WHERE id=?",
        Long.valueOf(picked.order().id()));
    failure("ORDER_STATE_CONFLICT", () -> cancel(picked));
  }

  @Test
  void supplementIsPerAdjustmentAndFifteenMinutesNeverClosesIt() {
    var c = paid();
    var first = approve(c, "101", 100);
    assertEquals("SUPPLEMENT", first.payment().purpose());
    now("2026-10-27T02:00:00Z");
    assertEquals(0, payments.closeExpired());
    result(start(first.payment().id()).attempt().id(), Result.SUCCESS);
    var second = approve(c, "102", 100);
    assertNotEquals(first.payment().id(), second.payment().id());
    result(start(second.payment().id()).attempt().id(), Result.SUCCESS);
    assertEquals(3, count("payment_orders"));
    assertEquals(3, count("payment_financial_events"));
    assertEquals(800, orders.get(user, c.order().id()).originalFeeCents());
    assertEquals(1000, orders.get(user, c.order().id()).effectiveFeeCents());
    assertEquals(
        2L,
        db.queryForObject(
            "SELECT count(*) FROM runrun_v2.v2_order_events WHERE"
                + " event_type='ADJUSTMENT_FUNDS_RESOLVED'",
            Long.class));
  }

  @Test
  void downgradeRefundCompletesOnlyOnReceiptAndCancellationSubtractsReservedMoney() {
    var c = paid();
    var adjustment = approve(c, "101", -100);
    RefundOrderVO down = adjustment.refunds().get(0);
    assertEquals(
        "PENDING_REFUND",
        db.queryForObject("SELECT status FROM runrun_v2.payment_adjustments", String.class));
    var cancelled = cancel(c);
    assertEquals(800, cancelled.refunds().stream().mapToLong(RefundOrderVO::amountCents).sum());
    assertEquals(
        700,
        cancelled.refunds().stream()
            .filter(r -> r.purpose().equals("CANCELLATION"))
            .findFirst()
            .orElseThrow()
            .amountCents());
    resultRefund(down.id(), Result.SUCCESS);
    assertEquals(
        "CANCELLED",
        db.queryForObject("SELECT status FROM runrun_v2.payment_adjustments", String.class));
  }

  @Test
  void downgradeRoundingUsesCumulativeOriginalEquity() {
    var c = paid();
    for (int i = 0; i < 3; i++) {
      var a = approve(c, Integer.toString(100 + i), -1);
      resultRefund(a.refunds().get(0).id(), Result.SUCCESS);
    }
    long platform =
        db.queryForObject(
            "SELECT sum(platform_cents) FROM runrun_v2.payment_financial_events WHERE"
                + " kind='DOWNGRADE_REFUND'",
            Long.class);
    // floor(3*240/800), rather than rounding each change up.
    assertEquals(0, platform);
    var fourth = approve(c, "104", -1);
    resultRefund(fourth.refunds().get(0).id(), Result.SUCCESS);
    assertEquals(
        1L,
        db.queryForObject(
            "SELECT sum(platform_cents) FROM runrun_v2.payment_financial_events WHERE"
                + " kind='DOWNGRADE_REFUND'",
            Long.class));
    assertEquals(
        4L,
        db.queryForObject(
            "SELECT sum(platform_cents+courier_cents) FROM runrun_v2.payment_financial_events WHERE"
                + " kind='DOWNGRADE_REFUND'",
            Long.class));
  }

  @Test
  void refundFailureReservesOriginalReferenceForSafeReconciliation() {
    var c = paid();
    var a = approve(c, "101", -100);
    var r = resultRefund(a.refunds().get(0).id(), Result.FAILED);
    assertEquals("FAILED", r.status());
    assertNull(r.succeededAt());
    assertEquals(
        700,
        cancel(c).refunds().stream()
            .filter(x -> x.purpose().equals("CANCELLATION"))
            .findFirst()
            .orElseThrow()
            .amountCents());
    resultRefund(r.id(), Result.SUCCESS);
    assertEquals(2, count("refund_orders"));
  }

  @Test
  void failedRefundRetriesSameReferenceWhileUnknownOnlyQueries() {
    var c = paid();
    var refund = cancel(c).refunds().get(0);
    var failed = resultRefund(refund.id(), Result.FAILED);
    var retried =
        new TransactionTemplate(transactions)
            .execute(
                s ->
                    payments.retryRefund(
                        admin, failed.id(), failed.version(), "合成退款重试确认", "retry-key"));
    assertEquals("PROCESSING", retried.status());
    assertEquals(failed.id(), retried.id());
    assertEquals(1, count("refund_orders"));
    var unknown = resultRefund(refund.id(), Result.UNKNOWN);
    var queried =
        new TransactionTemplate(transactions)
            .execute(
                s ->
                    payments.retryRefund(
                        admin, unknown.id(), unknown.version(), "合成未知结果先查", "query-key"));
    assertEquals("UNKNOWN", queried.status());
    resultRefund(refund.id(), Result.SUCCESS);
    assertEquals(1, count("refund_orders"));
  }

  @Test
  void insufficientRefundBalanceRollsBackApprovalAndEffectivePrice() {
    var c = paid();
    // An external adjustment service cannot manufacture money to refund: use
    // an effective-price fixture to exercise the independent receipt limit.
    db.update(
        "UPDATE runrun_v2.v2_orders SET effective_fee_cents=1600,version=version+1 WHERE id=?",
        Long.valueOf(c.order().id()));
    failure("AMOUNT_EXCEEDS_AVAILABLE", () -> approve(c, "101", -900));
    assertEquals(0, count("payment_adjustments"));
    assertEquals(0, count("refund_orders"));
    assertEquals(1600, orders.get(user, c.order().id()).effectiveFeeCents());
  }

  @Test
  void supplementLateAfterCancellationRefundsAndNeverReopens() {
    var c = paid();
    var f = approve(c, "101", 100);
    var a = start(f.payment().id());
    cancel(c);
    result(a.attempt().id(), Result.SUCCESS);
    assertEquals("CANCELLED", orders.get(user, c.order().id()).status());
    assertEquals(
        900L,
        db.queryForObject("SELECT sum(amount_cents) FROM runrun_v2.refund_orders", Long.class));
  }

  @Test
  void databaseGuardRejectsUnpaidPoolAndCrossOrderRefundReferences() {
    var c = create();
    assertThrows(
        DataAccessException.class,
        () ->
            db.update(
                "UPDATE runrun_v2.v2_orders SET status='PENDING_ASSIGNMENT',version=version+1 WHERE"
                    + " id=?",
                Long.valueOf(c.order().id())));
    var p = paid();
    var r = cancel(p).refunds().get(0);
    assertThrows(
        DataAccessException.class,
        () ->
            db.update(
                "UPDATE runrun_v2.refund_orders SET order_id=? WHERE id=?",
                Long.valueOf(c.order().id()),
                Long.valueOf(r.id())));
  }

  @Test
  void failedNotificationInsertRollsBackReceiptAndPoolAdmission() {
    var c = create();
    var a = start(c.originalPayment().id());
    db.execute(
        "ALTER TABLE runrun_v2.notification_outbox ADD CONSTRAINT synthetic_failure CHECK(event_key"
            + " NOT LIKE 'payment:%')");
    try {
      assertThrows(
          DataAccessException.class,
          () -> result(a.attempt().id(), Result.SUCCESS));
    } finally {
      db.execute("ALTER TABLE runrun_v2.notification_outbox DROP CONSTRAINT synthetic_failure");
    }
    assertEquals("PENDING_PAYMENT", orders.get(user, c.order().id()).status());
    assertEquals("PROCESSING", summary(c).payment().status());
    assertEquals(0, count("payment_financial_events"));
    result(a.attempt().id(), Result.SUCCESS);
    assertEquals("PAID", summary(c).payment().status());
  }

  @Test
  void concurrentCallbackCancellationAndTimeoutSerializeWithoutDeadlock() throws Exception {
    for (int i = 0; i < 5; i++) {
      now("2026-10-27T01:00:00Z");
      var c = create();
      var a = start(c.originalPayment().id());
      now("2026-10-27T01:30:00Z");
      String callbackKey = key(), cancelKey = key(), eventId = key();
      race(
          () ->
              payments.paymentResult(
                  admin,
                  a.attempt().id(),
                  callbackKey,
                  new PaymentResultRequest(0L, Result.SUCCESS, eventId)),
          () -> {
            try {
              return payments.cancel(
                  user, c.order().id(), cancelKey, new CancelRequest(0L, "合成竞争取消"));
            } catch (IdentityFailure ex) {
              assertEquals("ORDER_STATE_CONFLICT", ex.code);
              return null;
            }
          },
          payments::closeExpired);
      assertEquals("CANCELLED", orders.get(user, c.order().id()).status());
      assertEquals(1, summary(c).refunds().size());
    }
  }

  @Test
  void concurrentSuccessfulCallbackAndCancelAlwaysRefundOnce() throws Exception {
    var c = create();
    var a = start(c.originalPayment().id());
    race(
        () ->
            payments.paymentResult(
                admin,
                a.attempt().id(),
                "race-callback",
                new PaymentResultRequest(0L, Result.SUCCESS, "race-event")),
        () -> {
          try {
            return payments.cancel(
                user, c.order().id(), "race-cancel", new CancelRequest(0L, "合成竞争取消"));
          } catch (IdentityFailure ex) {
            assertEquals("ORDER_STATE_CONFLICT", ex.code);
            return cancel(c);
          }
        });
    assertEquals("CANCELLED", orders.get(user, c.order().id()).status());
    assertEquals(1, count("refund_orders"));
  }

  @SafeVarargs
  final void race(Callable<?>... actions) throws Exception {
    var pool = Executors.newFixedThreadPool(actions.length);
    var start = new CountDownLatch(1);
    try {
      var futures = new ArrayList<Future<?>>();
      for (var action : actions)
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  return action.call();
                }));
      start.countDown();
      for (var future : futures) future.get(20, TimeUnit.SECONDS);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void httpEnforcesAdminVersionsKeysAndRejectsClientAmounts() throws Exception {
    var c = create();
    var a = start(c.originalPayment().id());
    String url = "/api/v2/test/payment-attempts/" + a.attempt().id() + "/result";
    String content = json.write(new PaymentResultRequest(0L, Result.SUCCESS, "http-event"));
    http.perform(
            post(url)
                .header("Authorization", "Bearer " + user)
                .header("Idempotency-Key", "http")
                .contentType("application/json")
                .content(content))
        .andExpect(status().isForbidden());
    http.perform(
            post(url)
                .header("Authorization", "Bearer " + admin)
                .header("Idempotency-Key", "http")
                .contentType("application/json")
                .content(content))
        .andExpect(status().isOk());
    http.perform(
            post("/api/v2/payments/" + c.originalPayment().id() + "/attempts")
                .header("Authorization", "Bearer " + user)
                .header("Idempotency-Key", "bad-amount")
                .contentType("application/json")
                .content("{\"expectedVersion\":0,\"amountCents\":1}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    db.update(
        "UPDATE runrun_v2.app_users SET v2_admin_enabled=false,version=version+1 WHERE"
            + " role='ADMIN'");
    http.perform(
            post(url)
                .header("Authorization", "Bearer " + admin)
                .header("Idempotency-Key", "disabled")
                .contentType("application/json")
                .content(content))
        .andExpect(status().isForbidden());
  }
}
