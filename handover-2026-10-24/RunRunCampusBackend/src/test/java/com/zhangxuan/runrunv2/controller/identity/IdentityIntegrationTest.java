package com.zhangxuan.runrunv2.controller.identity;

import com.zhangxuan.runrunv2.service.identity.DeploymentIdentityService;
import com.zhangxuan.runrunv2.IdentityApplication;
import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.security.IdentityPermission;
import com.zhangxuan.runrunv2.security.IdentitySecrets;
import com.zhangxuan.runrunv2.service.identity.IdentityService;
import com.zhangxuan.runrunv2.bootstrap.IdentityStartupGuard;
import com.zhangxuan.runrunv2.integration.wechat.WechatGateway;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.zhangxuan.runrunv2.dto.response.identity.LoginResult;

@SpringBootTest(classes = IdentityApplication.class, properties = { "spring.config.name=application-v2-test", "rrc.identity.environment=test", "rrc.identity.app-id=synthetic-app", "rrc.identity.app-secret=synthetic-secret", "spring.flyway.enabled=false" })
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@Import(IdentityIntegrationTest.Probes.class)
// Close scheduled workers and JDBC pools with the class-scoped database fixture.
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class IdentityIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
            var sql = connection.createStatement()) {
            Path migrations = Path.of("../../server/db/migrations/up");
            for (String file : List.of("rrc_0001__create_v2_schema.sql", "rrc_0002__create_identity_foundation.sql", "rrc_0003__identity_runtime.sql")) sql.execute(Files.readString(migrations.resolve(file)));
        }
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("rrc.identity.encryption-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
        registry.add("rrc.identity.hash-key", () -> Base64.getEncoder().encodeToString("synthetic-different-hash-key-000001".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Autowired
    IdentityService identity;

    @Autowired
    DeploymentIdentityService deployment;

    @Autowired
    IdentitySecrets secrets;

    @Autowired
    JdbcTemplate db;

    @Autowired
    MockMvc http;

    @Autowired
    PlatformTransactionManager transactions;

    @MockitoBean
    WechatGateway wechat;

    @BeforeEach
    void resetData() {
        db.execute("TRUNCATE runrun_v2.app_users,runrun_v2.identity_tickets RESTART IDENTITY CASCADE");
        when(wechat.exchangeLoginCode(anyString())).thenAnswer(i -> "synthetic-openid:" + i.getArgument(0));
        when(wechat.exchangePhoneCode(anyString())).thenAnswer(i -> i.getArgument(0));
    }

    LoginResult register(String code, String phone, String role) {
        return identity.bindPhone(identity.login(code).loginTicket(), phone, role);
    }

    private long deployer() {
        return Long.parseLong(deployment.bootstrap("synthetic deployment setup", "CREATE DEPLOYMENT IDENTITY", "bootstrap"));
    }

    private LoginResult admin(long actor) {
        var account = register("admin", "13800000003", "USER");
        long id = Long.parseLong(account.account().id());
        deployment.provisionAdmin(actor, id, 0, "synthetic admin verified", "CREATE ADMIN " + id, "create-admin");
        return identity.login("admin");
    }

    private IdentityFailure failure(String code, org.junit.jupiter.api.function.Executable action) {
        IdentityFailure failure = assertThrows(IdentityFailure.class, action);
        assertEquals(code, failure.code);
        return failure;
    }

    @Test
    void userRegistersAndWechatLoginUsesTheExistingNewBinding() {
        var created = register("user", "13800000001", "USER");
        assertEquals("USER", created.account().role());
        assertEquals(created.account().id(), identity.login("user").account().id());
        assertEquals(created.account(), identity.authenticate(created.token()));
        assertEquals(1L, db.queryForObject("SELECT count(*) FROM runrun_v2.app_users", Long.class));
        assertEquals(1L, db.queryForObject("SELECT count(*) FROM runrun_v2.identity_audit_events WHERE event_type='WECHAT_BOUND'", Long.class));
        String ciphertext = db.queryForObject("SELECT openid_ciphertext FROM runrun_v2.wechat_identities", String.class);
        assertFalse(ciphertext.contains("synthetic-openid"));
        assertNotEquals(created.token(), db.queryForObject("SELECT token_hash FROM runrun_v2.user_sessions ORDER BY id LIMIT 1", String.class));
    }

    @Test
    void unapprovedCourierCanLoginAndReadOwnAccountButCannotAccept() throws Exception {
        var courier = register("courier", "13800000002", "COURIER");
        assertFalse(courier.account().courierEligible());
        assertEquals("COURIER", identity.requireRole(courier.token(), "COURIER", false).role());
        failure("COURIER_NOT_APPROVED", () -> identity.requireRole(courier.token(), "COURIER", true));
        http.perform(get("/api/v2/test/courier-write").header("Authorization", "Bearer " + courier.token())).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("COURIER_NOT_APPROVED"));
        db.update("UPDATE runrun_v2.courier_certifications SET eligible=true,status='APPROVED' WHERE courier_id=?", Long.valueOf(courier.account().id()));
        assertTrue(identity.requireRole(courier.token(), "COURIER", true).courierEligible());
    }

    @Test
    void repeatedTicketAndDuplicateIdentityAreRejected() {
        var first = identity.login("same");
        var second = identity.login("same");
        identity.bindPhone(first.loginTicket(), "13800000001", "USER");
        failure("AUTHENTICATION_FAILED", () -> identity.bindPhone(first.loginTicket(), "13800000001", "USER"));
        failure("IDENTITY_BINDING_CONFLICT", () -> identity.bindPhone(second.loginTicket(), "13800000002", "COURIER"));
        assertEquals(1L, db.queryForObject("SELECT count(*) FROM runrun_v2.app_users", Long.class));
    }

    @Test
    void matchingPhoneNeverRecoversOrRebindsNewUserCourierOrAdmin() {
        register("user", "13800000001", "USER");
        register("courier", "13800000002", "COURIER");
        admin(deployer());
        for (String phone : List.of("13800000001", "13800000002", "13800000003")) {
            String ticket = identity.login("attacker:" + phone).loginTicket();
            failure("IDENTITY_BINDING_CONFLICT", () -> identity.bindPhone(ticket, phone, "USER"));
        }
        assertEquals(3L, db.queryForObject("SELECT count(*) FROM runrun_v2.wechat_identities", Long.class));
    }

    @Test
    void expiredTicketsAndWechatFailuresCannotCreateAccounts() {
        String ticket = identity.login("expired").loginTicket();
        db.update("UPDATE runrun_v2.identity_tickets SET expires_at=now()-interval '1 second'");
        failure("AUTHENTICATION_FAILED", () -> identity.bindPhone(ticket, "13800000001", "USER"));
        when(wechat.exchangeLoginCode("bad")).thenThrow(new IdentityFailure(401, "WECHAT_AUTH_FAILED"));
        failure("WECHAT_AUTH_FAILED", () -> identity.login("bad"));
        String fresh = identity.login("fresh").loginTicket();
        when(wechat.exchangePhoneCode("bad-phone")).thenThrow(new IdentityFailure(401, "WECHAT_AUTH_FAILED"));
        failure("WECHAT_AUTH_FAILED", () -> identity.bindPhone(fresh, "bad-phone", "USER"));
        assertEquals(0L, db.queryForObject("SELECT count(*) FROM runrun_v2.app_users", Long.class));
    }

    @Test
    void publicSignupCannotChooseAdminOrSupplyTrustedIdentityFields() throws Exception {
        String ticket = identity.login("escalate").loginTicket();
        failure("VALIDATION_ERROR", () -> identity.bindPhone(ticket, "13800000001", "ADMIN"));
        http.perform(post("/api/v2/auth/wechat/login").contentType("application/json").content("{\"code\":\"x\",\"openid\":\"forged\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        http.perform(post("/api/v2/auth/wechat/bind-phone").contentType("application/json").content("{\"loginTicket\":\"x\",\"phoneCode\":\"x\",\"requestedRole\":\"USER\",\"phone\":\"13800000001\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void tokenTamperingExpiryAndSingleSessionRevocation() {
        var user = register("user", "13800000001", "USER");
        var second = identity.login("user");
        failure("AUTHENTICATION_FAILED", () -> identity.authenticate(secrets.token()));
        identity.revoke(user.token(), false, "logout-1");
        failure("AUTHENTICATION_FAILED", () -> identity.authenticate(user.token()));
        identity.authenticate(second.token());
        db.update("UPDATE runrun_v2.user_sessions SET expires_at=now()-interval '1 second'");
        failure("AUTHENTICATION_FAILED", () -> identity.authenticate(second.token()));
    }

    @Test
    void singleSessionLogoutIdempotencyBindsTheCurrentSession() {
        var user = register("user", "13800000001", "USER");
        var second = identity.login("user");
        identity.revoke(user.token(), false, "same-logout-key");
        failure("IDEMPOTENCY_KEY_CONFLICT", () -> identity.revoke(second.token(), false, "same-logout-key"));
        identity.authenticate(second.token());
    }

    @Test
    void revokeAllAndPasswordChangeInvalidateAllSessions() {
        var user = register("user", "13800000001", "USER");
        var second = identity.login("user");
        identity.setPassword(user.token(), "synthetic-password-123", "password-1");
        failure("AUTHENTICATION_FAILED", () -> identity.authenticate(user.token()));
        failure("AUTHENTICATION_FAILED", () -> identity.authenticate(second.token()));
        failure("AUTHENTICATION_FAILED", () -> identity.passwordLogin("13800000001", "wrong-password"));
        var passwordLogin = identity.passwordLogin("13800000001", "synthetic-password-123");
        var wechatLogin = identity.login("user");
        identity.revoke(passwordLogin.token(), true, "logout-all");
        failure("AUTHENTICATION_FAILED", () -> identity.authenticate(wechatLogin.token()));
    }

    @Test
    void suspendedAccountCannotLoginOrUseAnExistingSession() {
        var user = register("user", "13800000001", "USER");
        db.update("UPDATE runrun_v2.app_users SET status='SUSPENDED'");
        failure("AUTHENTICATION_FAILED", () -> identity.authenticate(user.token()));
        failure("AUTHENTICATION_FAILED", () -> identity.login("user"));
    }

    @Test
    void adminIsDisabledByDefaultAndBusinessBearerCannotGrantAccess() throws Exception {
        long actor = deployer();
        var admin = admin(actor);
        long target = Long.parseLong(admin.account().id());
        assertFalse(admin.account().adminEnabled());
        failure("V2_ADMIN_NOT_ENABLED", () -> identity.requireRole(admin.token(), "ADMIN", false));
        failure("ADMIN_ACCESS_MANAGEMENT_FORBIDDEN", () -> deployment.changeAccess(target, target, true, 1, "escalate", "ENABLE " + target, "self-grant"));
        http.perform(patch("/api/v2/admin/access/" + target).header("Authorization", "Bearer " + admin.token()).contentType("application/json").content("{\"enabled\":true}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ADMIN_ACCESS_MANAGEMENT_FORBIDDEN"));
        failure("AUTHENTICATION_FAILED", () -> identity.passwordLogin("deployment:anything", "synthetic-password"));
    }

    @Test
    void disableAndReenableImmediatelyInvalidateOldSessionsAndAuditVersions() {
        long actor = deployer();
        var initial = admin(actor);
        long id = Long.parseLong(initial.account().id());
        deployment.changeAccess(actor, id, true, 1, "enable verified admin", "ENABLE " + id, "enable");
        var enabled = identity.login("admin");
        identity.requireRole(enabled.token(), "ADMIN", false);
        failure("AUTHENTICATION_FAILED", () -> identity.authenticate(initial.token()));
        deployment.changeAccess(actor, id, false, 2, "disable verified admin", "DISABLE " + id, "disable");
        failure("AUTHENTICATION_FAILED", () -> identity.authenticate(enabled.token()));
        deployment.changeAccess(actor, id, true, 3, "reenable admin", "ENABLE " + id, "reenable");
        failure("AUTHENTICATION_FAILED", () -> identity.authenticate(enabled.token()));
        assertEquals(4L, identity.login("admin").account().securityVersion());
        assertEquals(3L, db.queryForObject("SELECT count(*) FROM runrun_v2.admin_audit_logs WHERE action='V2_ADMIN_ACCESS_CHANGED' AND administrator_id=? AND metadata->>'actorKind'='CONTROLLED_DEPLOYMENT'", Long.class, actor));
    }

    @Test
    void managementRequiresReasonConfirmationVersionAndActualDeploymentIdentity() {
        long actor = deployer();
        var admin = admin(actor);
        long target = Long.parseLong(admin.account().id());
        failure("VALIDATION_ERROR", () -> deployment.changeAccess(actor, target, true, 1, " ", "ENABLE " + target, "blank"));
        failure("VALIDATION_ERROR", () -> deployment.changeAccess(actor, target, true, 1, "reason", "yes", "unconfirmed"));
        failure("ORDER_STATE_CONFLICT", () -> deployment.changeAccess(actor, target, true, 0, "reason", "ENABLE " + target, "stale"));
        failure("FORBIDDEN", () -> deployment.changeAccess(actor, actor, true, 0, "reason", "ENABLE " + actor, "deployer-business"));
        failure("IDENTITY_BINDING_CONFLICT", () -> deployment.bootstrap("reason", "CREATE DEPLOYMENT IDENTITY", "second-bootstrap"));
    }

    @Test
    void accessManagementIdempotencyDoesNotBumpVersionTwiceAndRejectsChangedContent() {
        long actor = deployer();
        long id = Long.parseLong(admin(actor).account().id());
        var first = deployment.changeAccess(actor, id, true, 1, "reason", "ENABLE " + id, "same-key");
        assertEquals(first, deployment.changeAccess(actor, id, true, 1, "reason", "ENABLE " + id, "same-key"));
        failure("IDEMPOTENCY_KEY_CONFLICT", () -> deployment.changeAccess(actor, id, false, 1, "reason", "DISABLE " + id, "same-key"));
        assertEquals(1L, db.queryForObject("SELECT count(*) FROM runrun_v2.admin_audit_logs WHERE action='V2_ADMIN_ACCESS_CHANGED'", Long.class));
    }

    @Test
    void auditFailureRollsBackAccessChangeAndRevocation() {
        long actor = deployer();
        var admin = admin(actor);
        long id = Long.parseLong(admin.account().id());
        db.execute("CREATE FUNCTION runrun_v2.test_fail_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic failure'; END $$");
        db.execute("CREATE TRIGGER test_fail_audit BEFORE INSERT ON runrun_v2.admin_audit_logs FOR EACH ROW EXECUTE FUNCTION runrun_v2.test_fail_audit()");
        try {
            assertThrows(org.springframework.dao.DataAccessException.class, () -> deployment.changeAccess(actor, id, true, 1, "reason", "ENABLE " + id, "failing-audit"));
            assertFalse(identity.authenticate(admin.token()).adminEnabled());
            assertEquals(1L, identity.authenticate(admin.token()).securityVersion());
        } finally {
            db.execute("DROP TRIGGER test_fail_audit ON runrun_v2.admin_audit_logs");
            db.execute("DROP FUNCTION runrun_v2.test_fail_audit()");
        }
    }

    @Test
    void concurrentBindingsForSamePhoneCreateExactlyOneAccount() throws Exception {
        String first = identity.login("first").loginTicket();
        String second = identity.login("second").loginTicket();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> a = () -> {
                try {
                    identity.bindPhone(first, "13800000001", "USER");
                    return true;
                } catch (IdentityFailure e) {
                    assertEquals("IDENTITY_BINDING_CONFLICT", e.code);
                    return false;
                }
            };
            Callable<Boolean> b = () -> {
                try {
                    identity.bindPhone(second, "13800000001", "COURIER");
                    return true;
                } catch (IdentityFailure e) {
                    assertEquals("IDENTITY_BINDING_CONFLICT", e.code);
                    return false;
                }
            };
            var results = pool.invokeAll(List.of(a, b));
            assertNotEquals(results.get(0).get(), results.get(1).get());
            assertEquals(1L, db.queryForObject("SELECT count(*) FROM runrun_v2.app_users", Long.class));
            assertEquals(1L, db.queryForObject("SELECT count(*) FROM runrun_v2.wechat_identities", Long.class));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void businessWriteAndDisableSerializeOnTheSameAccount() throws Exception {
        long actor = deployer();
        long id = Long.parseLong(admin(actor).account().id());
        deployment.changeAccess(actor, id, true, 1, "enable", "ENABLE " + id, "enable");
        var admin = identity.login("admin");
        CountDownLatch authorized = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> write = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                identity.authorizeForWrite(admin.token(), "ADMIN", false);
                authorized.countDown();
                try {
                    assertTrue(release.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return null;
            }));
            assertTrue(authorized.await(10, TimeUnit.SECONDS));
            Future<?> disable = pool.submit(() -> deployment.changeAccess(actor, id, false, 2, "disable", "DISABLE " + id, "disable"));
            assertThrows(TimeoutException.class, () -> disable.get(200, TimeUnit.MILLISECONDS));
            release.countDown();
            write.get(10, TimeUnit.SECONDS);
            disable.get(10, TimeUnit.SECONDS);
            failure("AUTHENTICATION_FAILED", () -> new TransactionTemplate(transactions).execute(status -> identity.authorizeForWrite(admin.token(), "ADMIN", false)));
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void noLegacyAccountLookupOrRecoveryRoutes() throws Exception {
        db.execute("CREATE TABLE public.app_users(id bigint,phone text,role text)");
        try {
            db.update("INSERT INTO public.app_users VALUES(999,'13800000001','ADMIN')");
            var fresh = register("fresh", "13800000001", "USER");
            assertNotEquals("999", fresh.account().id());
            assertEquals("USER", fresh.account().role());
            assertThrows(IllegalStateException.class, () -> new IdentityStartupGuard(new com.zhangxuan.runrunv2.mapper.identity.IdentityStartupMapper(db), "test").run(null));
        } finally {
            db.execute("DROP TABLE public.app_users");
        }
        http.perform(post("/api/v2/admin/account-recoveries/1/resolve").contentType("application/json").content("{}")).andExpect(status().isForbidden());
        http.perform(post("/api/v1/auth/login").contentType("application/json").content("{}")).andExpect(status().isNotFound());
    }

    @Test
    void unknownV2HandlersDenyByDefaultAndResponsesArePrivate() throws Exception {
        var user = register("user", "13800000001", "USER");
        http.perform(get("/api/v2/test/unannotated").header("Authorization", "Bearer " + user.token())).andExpect(status().isForbidden());
        http.perform(get("/api/v2/test/admin").header("Authorization", "Bearer " + user.token())).andExpect(status().isForbidden());
        http.perform(get("/api/v2/auth/session").header("Authorization", "Bearer " + user.token())).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.openid").doesNotExist()).andExpect(jsonPath("$.phone").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        http.perform(get("/api/v2/auth/session")).andExpect(status().isUnauthorized());
        http.perform(post("/api/v2/auth/logout").header("Authorization", "Bearer " + user.token()).contentType("application/json").content("{}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void disabledPaymentSimulatorRoutesAreAbsent() throws Exception {
        for (String path : List.of("/api/v2/test/payment-attempts/1/result", "/api/v2/test/refunds/1/result")) http.perform(post(path).contentType("application/json").content("{}")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void productionAndMissingRuntimeMigrationFailClosed() {
        assertThrows(IllegalStateException.class, () -> new IdentityStartupGuard(new com.zhangxuan.runrunv2.mapper.identity.IdentityStartupMapper(db), "production").run(null));
        assertThrows(IllegalStateException.class, () -> new IdentitySecrets(Base64.getEncoder().encodeToString(new byte[32]), Base64.getEncoder().encodeToString(new byte[32])));
        assertNotEquals(secrets.encrypt("app", "synthetic"), secrets.encrypt("app", "synthetic"));
        assertNotEquals(secrets.hash("openid:app1", "synthetic"), secrets.hash("openid:app2", "synthetic"));
    }

    @RestController
    static class Probes {

        @GetMapping("/api/v2/test/courier-write")
        @IdentityPermission(role = "COURIER", courierEligible = true)
        String courier() {
            return "ok";
        }

        @GetMapping("/api/v2/test/admin")
        @IdentityPermission(role = "ADMIN")
        String admin() {
            return "ok";
        }

        @GetMapping("/api/v2/test/unannotated")
        String unannotated() {
            return "must not run";
        }
    }
}
