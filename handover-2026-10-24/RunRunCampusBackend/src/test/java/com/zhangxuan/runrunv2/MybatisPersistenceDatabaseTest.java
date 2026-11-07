package com.zhangxuan.runrunv2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhangxuan.runrunv2.config.OrderJson;
import com.zhangxuan.runrunv2.config.V2PersistenceConfiguration;
import com.zhangxuan.runrunv2.enums.order.ServiceType;
import com.zhangxuan.runrunv2.mapper.configuration.ServiceConfigMapper;
import com.zhangxuan.runrunv2.mapper.courier.CourierCertificationV2Mapper;
import com.zhangxuan.runrunv2.mapper.evidence.CourierEvidenceGovernanceMapper;
import com.zhangxuan.runrunv2.mapper.identity.IdentityMapper;
import com.zhangxuan.runrunv2.mapper.notification.NotificationOutboxMapper;
import com.zhangxuan.runrunv2.mapper.order.OrderMapper;
import com.zhangxuan.runrunv2.mapper.order.OrderReadMapper;
import com.zhangxuan.runrunv2.mapper.order.OrderStore;
import com.zhangxuan.runrunv2.mapper.payment.PaymentMapper;
import com.zhangxuan.runrunv2.model.order.OrderAddress;
import com.zhangxuan.runrunv2.model.order.OrderPricing;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Time;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** 验证项目映射与事务事实；JDBC 是独立事实检查端，不经过 Mapper 自证结果。 */
@Testcontainers
class MybatisPersistenceDatabaseTest {
    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    static JdbcTemplate db;
    static SqlSessionTemplate session;
    static TransactionTemplate tx;
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @BeforeAll
    static void initialize() throws Exception {
        var dataSource =
                new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        db = new JdbcTemplate(dataSource);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        Path root = Path.of("../..").toAbsolutePath().normalize();
        try (var connection = dataSource.getConnection()) {
            for (String migration :
                    Files.readAllLines(root.resolve("deploy/v2-test/migrations.txt"))) {
                if (!migration.isBlank()) {
                    try (var statement = connection.createStatement()) {
                        statement.execute(
                                Files.readString(
                                        root.resolve("server/db/migrations/up")
                                                .resolve(migration)));
                    }
                }
            }
        }
        db.execute("CREATE TABLE public.mybatis_test_sentinel(id bigint)");
        db.update("INSERT INTO public.mybatis_test_sentinel VALUES (42)");
        var config = new V2PersistenceConfiguration();
        var factory = config.v2SqlSessionFactory(dataSource, JSON);
        assertEquals(dataSource, factory.getConfiguration().getEnvironment().getDataSource());
        session = config.v2SqlSessionTemplate(factory, dataSource);
    }

    @Test
    void identityLongDefaultsAndRollbackUseTheSameSpringConnection() {
        var mapper = session.getMapper(IdentityMapper.class);
        db.queryForObject(
                "SELECT setval(pg_get_serial_sequence('runrun_v2.app_users','id'), 2147483648,"
                    + " false)",
                Long.class);
        String phone = UUID.randomUUID().toString();
        Long id =
                tx.execute(
                        status -> {
                            Long inserted = mapper.insertAccount(phone, "USER");
                            assertTrue(inserted > Integer.MAX_VALUE);
                            var account = mapper.selectById(inserted);
                            assertEquals("ACTIVE", account.getStatus());
                            assertEquals(false, account.getV2AdminEnabled());
                            assertEquals(0L, account.getVersion());
                            assertNull(account.getPasswordHash());
                            assertEquals(
                                    1L,
                                    db.queryForObject(
                                            "SELECT count(*) FROM runrun_v2.app_users WHERE id=?",
                                            Long.class,
                                            inserted));
                            status.setRollbackOnly();
                            return inserted;
                        });
        assertNull(mapper.selectById(id));
        mapper.insertAccount(phone, "USER");
        assertThrows(DuplicateKeyException.class, () -> mapper.insertAccount(phone, "USER"));
        Long courier = mapper.insertAccount(UUID.randomUUID().toString(), "COURIER");
        var certifications = session.getMapper(CourierCertificationV2Mapper.class);
        assertEquals(1, certifications.insertPendingCertification(courier));
        var certification = certifications.lockCourierCertification(courier).get(0);
        assertEquals("NOT_APPLIED", certification.get("status"));
        assertEquals(false, certification.get("eligible"));
        assertEquals(0L, certification.get("version"));
        assertEquals(
                42L, db.queryForObject("SELECT id FROM public.mybatis_test_sentinel", Long.class));
    }

    @Test
    void snapshotsMicrosecondsNullWritesAndMapDriverTypesArePreserved() {
        tx.executeWithoutResult(
                status -> {
                    var config = session.getMapper(ServiceConfigMapper.class);
                    var settings = config.lockSettings(true);
                    assertTrue(settings.containsKey("review_owner_id"));
                    assertNull(settings.get("review_owner_id"));
                    assertInstanceOf(Time.class, settings.get("business_open"));
                    assertEquals(1, config.updateSettings(false, null));
                    assertNull(config.lockSettings(false).get("review_owner_id"));
                    assertEquals(config.findZones(true).size(), config.findZones(false).size());
                    var zone = config.findEnabledZone("STANDARD").get(0);
                    assertInstanceOf(Long.class, zone.get("package_surcharge_cents"));
                    assertInstanceOf(java.sql.Timestamp.class, zone.get("created_at"));
                    String pointCode = UUID.randomUUID().toString();
                    config.upsertServicePoint(pointCode, "合成点位", null, null, "GCJ02", true, 0);
                    var point = config.findEnabledServicePoint(pointCode).get(0);
                    assertTrue(point.containsKey("latitude"));
                    assertNull(point.get("latitude"));
                    Long user =
                            session.getMapper(IdentityMapper.class)
                                    .insertAccount(UUID.randomUUID().toString(), "USER");
                    var address =
                            new OrderAddress(
                                    "合成地址",
                                    "STANDARD",
                                    "其他区域",
                                    pointCode,
                                    "合成点位",
                                    null,
                                    null,
                                    "GCJ02");
                    long cents = 4_294_967_297L;
                    var pricing =
                            new OrderPricing(
                                    ServiceType.PACKAGE_XL, cents, "STANDARD", 0, cents, 0);
                    var time = OffsetDateTime.parse("2030-01-02T12:34:56.123456+05:30");
                    OrderJson json = new OrderJson(JSON);
                    String id =
                            session.getMapper(OrderMapper.class)
                                    .insertOrder(
                                            UUID.randomUUID().toString(),
                                            user,
                                            "PACKAGE_XL",
                                            (Long) zone.get("id"),
                                            (Long) point.get("id"),
                                            json.write(address),
                                            json.write(pricing),
                                            "{\"nullable\":null,\"nested\":{\"n\":7}}",
                                            cents,
                                            cents,
                                            time.plusMinutes(30),
                                            time,
                                            time);
                    var store = new OrderStore(session.getMapper(OrderReadMapper.class));
                    var order = store.read(id, true);
                    assertEquals(address, order.addressSnapshot());
                    assertEquals(pricing, order.pricingSnapshot());
                    assertEquals(cents, order.originalFeeCents());
                    assertTrue(order.policySnapshot().containsKey("nullable"));
                    assertEquals(time.toInstant(), order.createdAt().toInstant());
                    assertEquals(123456000, order.createdAt().getNano());
                    assertEquals(
                            time.plusMinutes(30).toInstant(),
                            order.pendingPaymentDeadline().toInstant());
                    db.update(
                            "UPDATE runrun_v2.v2_orders SET"
                                + " confirm_started_at=?,confirm_deadline=?,version=version+1 WHERE"
                                + " id=?",
                            time,
                            time.plusMinutes(10),
                            Long.valueOf(id));
                    assertEquals(
                            1,
                            session.getMapper(PaymentMapper.class)
                                    .updateEffectiveFeeAndClearConfirmation(
                                            cents, time, Long.valueOf(id)));
                    order = store.read(id, false);
                    assertNull(order.confirmStartedAt());
                    assertNull(order.confirmDeadline());
                    assertEquals(2L, order.version());
                    status.setRollbackOnly();
                });
        assertThrows(
                EmptyResultDataAccessException.class,
                () ->
                        session.getMapper(CourierEvidenceGovernanceMapper.class)
                                .lockOrder(Long.MAX_VALUE));
        tx.executeWithoutResult(
                status ->
                        assertNull(
                                session.getMapper(IdentityMapper.class)
                                        .lockIdentityScope("synthetic-mybatis-contract")
                                        .get(0)));
    }

    @Test
    void concurrentOutboxClaimsRespectLeaseAndFailedRetryAndJsonb() throws Exception {
        Long user =
                session.getMapper(IdentityMapper.class)
                        .insertAccount(UUID.randomUUID().toString(), "USER");
        for (int index = 0; index < 4; index++) {
            db.update(
                    "INSERT INTO"
                        + " runrun_v2.notification_outbox(recipient_id,event_key,channel,payload)"
                        + " VALUES (?,?,'WECHAT',?::jsonb)",
                    user,
                    "mapper-contract:" + index,
                    "{\"kind\":\"合成通知\",\"nullable\":null,\"nested\":{\"n\":7}}");
        }
        var mapper = session.getMapper(NotificationOutboxMapper.class);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first =
                    pool.submit(
                            () -> {
                                start.await();
                                return tx.execute(s -> mapper.claimOutboxDeliveries(2));
                            });
            var second =
                    pool.submit(
                            () -> {
                                start.await();
                                return tx.execute(s -> mapper.claimOutboxDeliveries(2));
                            });
            start.countDown();
            var claims = new HashSet<Long>();
            for (var future : java.util.List.of(first, second)) {
                var deliveries = future.get(15, TimeUnit.SECONDS);
                assertEquals(2, deliveries.size());
                for (var delivery : deliveries) {
                    assertTrue(claims.add(delivery.id()));
                    assertEquals("合成通知", delivery.payload().get("kind"));
                    assertTrue(delivery.payload().containsKey("nullable"));
                    assertEquals(Map.of("n", 7), delivery.payload().get("nested"));
                }
            }
            assertEquals(4, claims.size());
            assertTrue(tx.execute(s -> mapper.claimOutboxDeliveries(10)).isEmpty());
            long expired = claims.iterator().next();
            db.update(
                    "UPDATE runrun_v2.notification_outbox SET locked_until=now()-interval '1"
                        + " second' WHERE id=?",
                    expired);
            var retry = tx.execute(s -> mapper.claimOutboxDeliveries(1));
            assertEquals(expired, retry.get(0).id());
            assertEquals(2, retry.get(0).attemptCount());
            tx.executeWithoutResult(
                    s -> mapper.completeDelivery(expired, false, "SYNTHETIC_FAILURE"));
            assertEquals(
                    "FAILED",
                    db.queryForObject(
                            "SELECT status FROM runrun_v2.notification_outbox WHERE id=?",
                            String.class,
                            expired));
            assertTrue(tx.execute(s -> mapper.claimOutboxDeliveries(10)).isEmpty());
            db.update(
                    "UPDATE runrun_v2.notification_outbox SET next_attempt_at=now()-interval '1"
                        + " second' WHERE id=?",
                    expired);
            assertEquals(3, tx.execute(s -> mapper.claimOutboxDeliveries(1)).get(0).attemptCount());
            tx.executeWithoutResult(s -> mapper.completeDelivery(expired, true, null));
            assertEquals(
                    "SENT",
                    db.queryForObject(
                            "SELECT status FROM runrun_v2.notification_outbox WHERE id=?",
                            String.class,
                            expired));
            assertEquals(
                    42L,
                    db.queryForObject("SELECT id FROM public.mybatis_test_sentinel", Long.class));
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
