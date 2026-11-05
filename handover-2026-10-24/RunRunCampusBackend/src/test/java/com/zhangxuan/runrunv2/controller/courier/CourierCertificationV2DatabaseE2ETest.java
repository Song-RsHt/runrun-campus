package com.zhangxuan.runrunv2.controller.courier;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
class CourierCertificationV2DatabaseE2ETest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test void certificationLifecycleRunsAgainstIsolatedRunrunV2Schema() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        Path root = Path.of("..", "..", "server", "db", "migrations", "up").toAbsolutePath().normalize();
        try (Connection c = ds.getConnection()) {
            for (String name : new String[]{"rrc_0001__create_v2_schema.sql", "rrc_0002__create_identity_foundation.sql", "rrc_0003__identity_runtime.sql", "rrc_0006__courier_evidence_governance.sql", "rrc_0100__orders_and_service_config.sql", "rrc_0101__evidence_order_constraints.sql"}) {
                try (Statement migration = c.createStatement()) {
                    migration.execute(java.nio.file.Files.readString(root.resolve(name)));
                }
            }
            try (Statement s = c.createStatement()) {
                s.execute("INSERT INTO runrun_v2.app_users(phone,role) VALUES ('13800000001','COURIER'),('13800000002','ADMIN')");
                s.execute("INSERT INTO runrun_v2.courier_certifications(courier_id,status,submitted_at) VALUES (1,'PENDING',now())");
                s.execute("UPDATE runrun_v2.courier_certifications SET submitted_photo_resource_id='student-card-e2e',submitted_photo_storage_key='private/e2e.jpg',version=version+1 WHERE id=1 AND version=0");
                s.execute("UPDATE runrun_v2.courier_certifications SET status='APPROVED',eligible=true,approved_photo_resource_id=submitted_photo_resource_id,approved_photo_storage_key=submitted_photo_storage_key,submitted_photo_resource_id=NULL,submitted_photo_storage_key=NULL,reviewed_at=now(),version=version+1 WHERE id=1 AND version=1");
            }
            try (var q = c.prepareStatement("SELECT status,eligible,version,approved_photo_resource_id FROM runrun_v2.courier_certifications WHERE id=1")) {
                var rs=q.executeQuery(); rs.next(); assertEquals("APPROVED",rs.getString(1)); assertEquals(true,rs.getBoolean(2)); assertEquals(2,rs.getLong(3)); assertEquals("student-card-e2e",rs.getString(4));
            }
            try (var q = c.prepareStatement("SELECT count(*) FROM pg_constraint WHERE conname IN ('evidence_holds_order_fk','evidence_deletion_order_fk')")) {
                var rs=q.executeQuery(); rs.next(); assertEquals(2,rs.getInt(1));
            }
        }
    }
}
