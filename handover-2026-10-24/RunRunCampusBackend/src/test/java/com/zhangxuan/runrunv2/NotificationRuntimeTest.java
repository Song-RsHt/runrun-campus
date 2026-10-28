package com.zhangxuan.runrunv2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Notification assertions deliberately qualify runrun_v2; there is no public fallback. */
@Testcontainers
class NotificationRuntimeTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void outboxIsCreatedInTheIndependentSchemaOnly() throws Exception {
        Path root = Path.of("..", "..", "server", "db", "migrations", "up").toAbsolutePath().normalize();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            for (String migration : List.of(
                    "rrc_0001__create_v2_schema.sql", "rrc_0002__create_identity_foundation.sql",
                    "rrc_0003__identity_runtime.sql", "rrc_0100__orders_and_service_config.sql",
                    "rrc_0200__mock_payments.sql")) {
                try (var statement = connection.createStatement()) {
                    statement.execute(Files.readString(root.resolve(migration)));
                }
            }
            try (var query = connection.createStatement();
                 var result = query.executeQuery("SELECT to_regclass('runrun_v2.notification_outbox'), to_regclass('public.notification_outbox'), count(*) FROM runrun_v2.notification_outbox")) {
                assertTrue(result.next());
                assertEquals("runrun_v2.notification_outbox", result.getString(1));
                assertEquals(null, result.getString(2));
                assertEquals(0L, result.getLong(3));
            }
        }
    }
}
