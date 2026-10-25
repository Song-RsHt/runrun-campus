package com.zhangxuan.runruncampus.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionFoundationTest {
    @Test
    void separatesDevelopmentAndProductionConfiguration() {
        Properties common = yaml("application.yml");
        Properties development = yaml("application-dev.yml");
        Properties production = yaml("application-prod.yml");

        assertEquals("dev", common.getProperty("spring.profiles.default"));
        assertEquals("validate", common.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("true", development.getProperty("spring.flyway.baseline-on-migrate"));
        assertEquals("false", production.getProperty("spring.flyway.baseline-on-migrate"));
        assertEquals("configtree:/run/secrets/", production.getProperty("spring.config.import"));
        assertEquals("${SPRING_DATASOURCE_URL}", production.getProperty("spring.datasource.url"));
        assertEquals("${SPRING_DATASOURCE_PASSWORD}", production.getProperty("spring.datasource.password"));
        assertEquals("local", development.getProperty("app.proof-storage.type"));
        assertEquals("${PROOF_STORAGE_DIR:uploads/order-proofs}",
                development.getProperty("app.proof-storage.local-directory"));
        assertEquals("${PROOF_STORAGE_TYPE:oss}", production.getProperty("app.proof-storage.type"));
        assertEquals("${OSS_REGION}", production.getProperty("app.proof-storage.oss.region"));
        assertEquals("${OSS_BUCKET}", production.getProperty("app.proof-storage.oss.bucket"));
        assertEquals("${OSS_ECS_ROLE_NAME}",
                production.getProperty("app.proof-storage.oss.ecs-role-name"));
    }

    @Test
    void packagesAdminFrontendWithBackend() {
        assertTrue(new ClassPathResource("static/index.html").exists());
    }

    @Test
    void providesNonDestructiveVersionedSchemaMigrations() throws IOException {
        String baseline = resourceText("db/migration/V1__baseline_schema.sql");
        String indexes = resourceText("db/migration/V2__production_indexes.sql");
        String packagePickup = resourceText("db/migration/V13__package_pickup_service_types.sql");
        String extraLargePackage = resourceText("db/migration/V18__extra_large_package.sql");
        String finerPackageTypes = resourceText("db/migration/V39__finer_package_service_types.sql");

        List.of("app_users", "campus_orders", "courier_certifications", "order_events", "order_issues",
                "order_messages", "order_proofs", "user_addresses", "user_coupons", "user_feedback",
                "user_memberships", "user_sessions")
                .forEach(table -> assertTrue(baseline.contains("CREATE TABLE " + table), table));
        assertTrue(indexes.contains("CREATE INDEX"));
        assertTrue(packagePickup.contains("PACKAGE_SMALL"));
        assertTrue(packagePickup.contains("PACKAGE_MEDIUM"));
        assertTrue(packagePickup.contains("PACKAGE_LARGE"));
        assertTrue(extraLargePackage.contains("PACKAGE_XL"));
        assertTrue(finerPackageTypes.contains("PACKAGE_MEDIUM_LARGE"));
        assertTrue(finerPackageTypes.contains("PACKAGE_EXTRA_LARGE"));
        assertTrue(finerPackageTypes.contains("service_type TYPE VARCHAR(32)"));
        assertTrue(finerPackageTypes.contains("proposed_package_service_type TYPE VARCHAR(32)"));
        assertFalse((baseline + indexes + packagePickup + extraLargePackage + finerPackageTypes)
                .toUpperCase().contains("DROP TABLE"));
    }

    private Properties yaml(String name) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource(name));
        return factory.getObject();
    }

    private String resourceText(String name) throws IOException {
        return new ClassPathResource(name).getContentAsString(StandardCharsets.UTF_8);
    }
}
