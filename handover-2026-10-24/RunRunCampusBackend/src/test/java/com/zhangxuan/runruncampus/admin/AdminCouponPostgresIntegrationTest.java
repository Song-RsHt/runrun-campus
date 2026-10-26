package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRepository;
import com.zhangxuan.runruncampus.user.UserRole;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "app.proof-storage.local-directory=target/test-coupon-proofs")
@ActiveProfiles("test")
@Testcontainers
class AdminCouponPostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired AdminCouponService service;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    @Test
    void emptyDatabaseMigratesToLatestAndPersistsCouponWithAuditLog() {
        assertEquals("43", jdbc.queryForObject("""
                select version from flyway_schema_history
                where success = true order by installed_rank desc limit 1
                """, String.class));
        AppUser administrator = new AppUser(phone(), "hash", "优惠券运营", UserRole.ADMIN);
        administrator.changeAdminRole(AdminRole.OPERATIONS);
        administrator = users.saveAndFlush(administrator);
        AppUser customer = users.saveAndFlush(new AppUser(phone(), "hash", "获券用户", UserRole.CUSTOMER));

        var result = service.issueOneYuan(administrator, customer.getId());

        assertEquals(100, result.discountCents());
        assertEquals(200, result.minimumOrderCents());
        assertEquals("管理员赠送1元券", result.title());
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from user_coupons
                where id = ? and user_id = ? and discount_cents = 100 and minimum_order_cents = 200
                """, Integer.class, result.couponId(), customer.getId()));
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from admin_audit_logs
                where administrator_id = ? and action = 'COUPON_ISSUED'
                  and target_type = 'USER' and target_id = ?
                """, Integer.class, administrator.getId(), customer.getId()));
    }

    @Test
    void upgradeFromV25PreservesExistingAuditAndAcceptsCouponIssuedAction() {
        String schema = "coupon_issue_upgrade";
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).target("25").load().migrate();
        jdbc.update("""
                insert into coupon_issue_upgrade.app_users
                    (id, created_at, display_name, password_hash, phone, role, admin_role)
                values (1, now(), '迁移管理员', 'hash', '19900000001', 'ADMIN', 'OPERATIONS')
                """);
        jdbc.update("""
                insert into coupon_issue_upgrade.admin_audit_logs
                    (action, target_type, target_id, summary, created_at, administrator_id)
                values ('ORDER_DISPATCHED', 'ORDER', 9, '迁移前审计记录', now(), 1)
                """);

        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).load().migrate();

        assertEquals("43", jdbc.queryForObject("""
                select version from coupon_issue_upgrade.flyway_schema_history
                where success = true order by installed_rank desc limit 1
                """, String.class));
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from coupon_issue_upgrade.admin_audit_logs
                where action = 'ORDER_DISPATCHED' and summary = '迁移前审计记录'
                """, Integer.class));
        jdbc.update("""
                insert into coupon_issue_upgrade.admin_audit_logs
                    (action, target_type, target_id, summary, created_at, administrator_id)
                values ('COUPON_ISSUED', 'USER', 2, '迁移后优惠券审计', now(), 1)
                """);
        String constraint = jdbc.queryForObject("""
                select pg_get_constraintdef(oid) from pg_constraint
                where conname = 'admin_audit_logs_action_check'
                  and conrelid = 'coupon_issue_upgrade.admin_audit_logs'::regclass
                """, String.class);
        assertTrue(constraint.contains("COUPON_ISSUED"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                insert into coupon_issue_upgrade.admin_audit_logs
                    (action, target_type, target_id, summary, created_at, administrator_id)
                values ('UNKNOWN_ACTION', 'USER', 2, '非法动作', now(), 1)
                """));
    }

    private String phone() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 11);
    }
}
