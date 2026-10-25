package com.zhangxuan.runruncampus.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionConfigurationValidatorTest {
    private static final String DATABASE_PASSWORD = "Db!90123456789abcdef0123456789ab";
    private static final String ADMIN_PASSWORD = "Admin!9-0123456789abcdef";

    @Test
    void acceptsHardenedProductionConfiguration() {
        assertDoesNotThrow(() -> validator(
                verifiedUrl(),
                "runrun_app", DATABASE_PASSWORD, ADMIN_PASSWORD, true)
                .run(new DefaultApplicationArguments()));
    }

    @Test
    void rejectsDatabaseWithoutVerifiedTls() {
        assertThrows(IllegalStateException.class, () -> validator(
                "jdbc:postgresql://db.internal:5432/runrun_campus?sslmode=require&sslrootcert=/run/secrets/RDS_CA_CERTIFICATE",
                "runrun_app", DATABASE_PASSWORD, ADMIN_PASSWORD, true)
                .run(new DefaultApplicationArguments()));
        assertThrows(IllegalStateException.class, () -> validator(
                "jdbc:postgresql://db.internal:5432/runrun_campus?sslmode=verify-full",
                "runrun_app", DATABASE_PASSWORD, ADMIN_PASSWORD, true)
                .run(new DefaultApplicationArguments()));
    }

    @Test
    void rejectsWeakOrReusedSecrets() {
        assertThrows(IllegalStateException.class, () -> validator(
                verifiedUrl(),
                "runrun_app", "short", ADMIN_PASSWORD, true)
                .run(new DefaultApplicationArguments()));
        assertThrows(IllegalStateException.class, () -> validator(
                verifiedUrl(),
                "runrun_app", DATABASE_PASSWORD, DATABASE_PASSWORD, true)
                .run(new DefaultApplicationArguments()));
    }

    @Test
    void rejectsSuperuserAndPublicOssEndpoint() {
        assertThrows(IllegalStateException.class, () -> validator(
                verifiedUrl(),
                "postgres", DATABASE_PASSWORD, ADMIN_PASSWORD, true)
                .run(new DefaultApplicationArguments()));
        assertThrows(IllegalStateException.class, () -> validator(
                verifiedUrl(),
                "runrun_app", DATABASE_PASSWORD, ADMIN_PASSWORD, false)
                .run(new DefaultApplicationArguments()));
    }

    private ProductionConfigurationValidator validator(String url, String username, String databasePassword,
                                                       String adminPassword, boolean internalEndpoint) {
        return new ProductionConfigurationValidator(url, username, databasePassword, "13800000000", adminPassword,
                "oss", "cn-heyuan", "runrun-campus-private", "RunRunCampusOssRole", internalEndpoint);
    }

    private String verifiedUrl() {
        return "jdbc:postgresql://db.internal:5432/runrun_campus"
                + "?sslmode=verify-full&sslrootcert=/run/secrets/RDS_CA_CERTIFICATE";
    }
}
