package com.zhangxuan.runrunv2;

import com.zhangxuan.runrunv2.service.courier.CourierCertificationV2Service;
import com.zhangxuan.runrunv2.service.evidence.CourierEvidenceGovernanceService;
import com.zhangxuan.runrunv2.service.identity.IdentityService;
import com.zhangxuan.runrunv2.service.idempotency.OrderIdempotency;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.zhangxuan.runrunv2.dto.response.identity.Account;

/**
 * Exercise JDBC bindings against the delivered schema, rather than mocking the driver.
 */
@Testcontainers
class V2JdbcContractDatabaseTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    static JdbcTemplate db;

    static TransactionTemplate tx;

    @BeforeAll
    static void migrate() throws Exception {
        var ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        db = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        Path root = Path.of("../..").toAbsolutePath().normalize();
        try (var connection = ds.getConnection()) {
            for (String name : Files.readAllLines(root.resolve("deploy/v2-test/migrations.txt"))) {
                if (name.isBlank())
                    continue;
                try (var statement = connection.createStatement()) {
                    statement.execute(Files.readString(root.resolve("server/db/migrations/up").resolve(name)));
                }
            }
        }
    }

    @Test
    void performanceExecutesAgainstTheDeliveredSchema() {
        var identity = mock(IdentityService.class);
        when(identity.requireRole("admin-session", "ADMIN", false)).thenReturn(new Account("999", "ADMIN", "ACTIVE", true, 0, false));
        var service = new CourierCertificationV2Service(new com.zhangxuan.runrunv2.mapper.courier.CourierCertificationV2Mapper(db), identity, null, null, null);
        Map<String, Object> result = service.performance("admin-session", 999, 0, 20);
        assertEquals(0L, result.get("totalCompletedPackageOrders"));
        assertEquals(Map.of("items", java.util.List.of(), "page", 0, "size", 20, "totalElements", 0L, "totalPages", 0L), result.get("orders"));
        assertDoesNotThrow(() -> service.performance("admin-session", 999, Integer.MAX_VALUE, 100));
    }

    @Test
    void evidenceHoldPersistsItsInstantAndNullableOrderReference() {
        long admin = db.queryForObject("INSERT INTO runrun_v2.app_users(phone,role) VALUES('jdbc-admin','ADMIN') RETURNING id", Long.class);
        long courier = db.queryForObject("INSERT INTO runrun_v2.app_users(phone,role) VALUES('jdbc-courier','COURIER') RETURNING id", Long.class);
        long certification = db.queryForObject("INSERT INTO runrun_v2.courier_certifications(courier_id,status) VALUES(?,'PENDING') RETURNING id", Long.class, courier);
        var identity = mock(IdentityService.class);
        when(identity.authorizeForWrite("admin-session", "ADMIN", false)).thenReturn(new Account(Long.toString(admin), "ADMIN", "ACTIVE", true, 0, false));
        var service = new CourierEvidenceGovernanceService(new com.zhangxuan.runrunv2.mapper.evidence.CourierEvidenceGovernanceMapper(db), null, identity, mock(OrderIdempotency.class));
        Instant due = Instant.parse("2030-01-02T03:04:05.123456Z");
        var result = tx.execute(status -> service.hold("admin-session", null, certification, "synthetic legal hold", due, "jdbc-hold"));
        assertNotNull(result);
        assertEquals(due, db.queryForObject("SELECT review_due_at FROM runrun_v2.evidence_holds WHERE id=?", (r, n) -> r.getObject(1, OffsetDateTime.class).toInstant(), result.id()));
        assertEquals(1L, db.queryForObject("SELECT count(*) FROM runrun_v2.admin_audit_logs WHERE target_id=? AND action='EVIDENCE_HOLD_CREATED' AND metadata->>'certificationId'=? AND metadata->>'orderId' IS NULL", Long.class, result.id(), Long.toString(certification)));
    }

    @Test
    void deletionRequestPersistsItsInstantWithoutChangingTheResource() {
        long courier = db.queryForObject("INSERT INTO runrun_v2.app_users(phone,role) VALUES('jdbc-deletion-courier','COURIER') RETURNING id", Long.class);
        long certification = db.queryForObject("INSERT INTO runrun_v2.courier_certifications(courier_id,status) VALUES(?,'PENDING') RETURNING id", Long.class, courier);
        var service = new CourierEvidenceGovernanceService(new com.zhangxuan.runrunv2.mapper.evidence.CourierEvidenceGovernanceMapper(db), null, null, null);
        Instant due = Instant.parse("2030-02-03T04:05:06.654321Z");
        tx.executeWithoutResult(status -> service.requestCertificationDeletion(certification, "synthetic-photo", "synthetic/photo.png", due));
        assertEquals(due, db.queryForObject("SELECT delete_after FROM runrun_v2.evidence_deletion_audits WHERE certification_id=? AND resource_id='synthetic-photo' AND status='REQUESTED'", (r, n) -> r.getObject(1, OffsetDateTime.class).toInstant(), certification));
    }
}
