package com.zhangxuan.runruncampus.auth;

import com.zhangxuan.runruncampus.admin.AdminRole;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRepository;
import com.zhangxuan.runruncampus.user.UserRole;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.proof-storage.local-directory=target/test-order-proofs",
        "app.admin-mfa.key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="})
@ActiveProfiles("test")
@Testcontainers
class AdminMfaPostgresIntegrationTest {
    @Container private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final AtomicInteger NUMBER = new AtomicInteger(100);
    private static final String PASSWORD = "MfaTestPassword2026";

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private AdminMfaService mfa;
    @Autowired private AuthService auth;
    @Autowired private UserRepository users;
    @Autowired private SessionRepository sessions;
    @Autowired private PasswordEncoder passwords;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private TestRestTemplate http;

    private AppUser admin(AdminRole role) {
        AppUser user = new AppUser("19904000" + NUMBER.incrementAndGet(), passwords.encode(PASSWORD), "QA MFA", UserRole.ADMIN);
        user.changeAdminRole(role);
        return users.saveAndFlush(user);
    }

    private Enrollment enroll(AppUser user) {
        var challenge = mfa.begin(user.getPhone(), PASSWORD);
        assertEquals("ENROLL", challenge.step());
        var verification = mfa.verify(challenge.challengeToken(), AdminMfaCrypto.code(challenge.secret(), Instant.now().getEpochSecond() / 30 - 1));
        return new Enrollment(challenge.secret(), verification);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUPER_ADMIN", "OPERATIONS", "SUPPORT", "REVIEWER", "AUDITOR"})
    void allRolesRequireSecondFactorAndPreservePermissions(String role) {
        AppUser user = admin(AdminRole.valueOf(role));
        Session legacy = sessions.saveAndFlush(new Session(AdminMfaCrypto.randomToken(), user, Instant.now().plusSeconds(3600)));
        assertThrows(ResponseStatusException.class, () -> auth.login(user.getPhone(), PASSWORD));
        assertThrows(ResponseStatusException.class, () -> auth.requireUser("Bearer " + legacy.getToken()));
        var challenge = mfa.begin(user.getPhone(), PASSWORD);
        assertThrows(ResponseStatusException.class, () -> auth.requireUser("Bearer " + challenge.challengeToken()));
        var result = mfa.verify(challenge.challengeToken(), AdminMfaCrypto.code(challenge.secret(), Instant.now().getEpochSecond() / 30));
        assertEquals(10, result.recoveryCodes().size());
        assertEquals(10, result.recoveryCodes().stream().distinct().count());
        assertEquals(AdminRole.valueOf(role).getPermissions(), result.session().user().permissions());
        assertEquals(user.getId(), auth.requireAdmin("Bearer " + result.session().token()).getId());
        assertThrows(ResponseStatusException.class, () -> mfa.verify(challenge.challengeToken(), result.recoveryCodes().get(0)));
        String encrypted = jdbc.queryForObject("select secret_ciphertext from admin_mfa_credentials where user_id = ?", String.class, user.getId());
        String hashes = jdbc.queryForObject("select recovery_hashes from admin_mfa_credentials where user_id = ?", String.class, user.getId());
        assertFalse(encrypted.contains(challenge.secret()));
        result.recoveryCodes().forEach(code -> assertFalse(hashes.contains(code)));
        assertTrue(sessions.findByToken(legacy.getToken()).isEmpty());
    }

    @Test
    void expiresAndReplacesChallengesWithoutGrantingASession() {
        AppUser user = admin(AdminRole.OPERATIONS);
        var first = mfa.begin(user.getPhone(), PASSWORD);
        var second = mfa.begin(user.getPhone(), PASSWORD);
        assertThrows(ResponseStatusException.class, () -> mfa.verify(first.challengeToken(), "123456"));
        jdbc.update("update admin_mfa_credentials set challenge_expires_at = now() - interval '1 second' where user_id = ?", user.getId());
        assertThrows(ResponseStatusException.class, () -> mfa.verify(second.challengeToken(), AdminMfaCrypto.code(second.secret(), Instant.now().getEpochSecond() / 30)));
        assertEquals(0, jdbc.queryForObject("select count(*) from user_sessions where user_id = ?", Integer.class, user.getId()));
    }

    @Test
    void sessionCenterPreservesCurrentAdminSessionAndRevokesOtherDevices() {
        AppUser user = admin(AdminRole.OPERATIONS);
        Enrollment first = enroll(user);
        var challenge = mfa.begin(user.getPhone(), PASSWORD);
        var second = mfa.verify(challenge.challengeToken(),
                AdminMfaCrypto.code(first.secret(), Instant.now().getEpochSecond() / 30),
                AuthService.SessionMetadata.of("ADMIN_WEB", "Chrome on Windows"));

        var active = auth.sessions("Bearer " + second.session().token());

        assertEquals(2, active.size());
        assertEquals("Chrome on Windows", active.stream().filter(AuthService.SessionInfo::current)
                .findFirst().orElseThrow().deviceName());
        assertEquals(1, auth.revokeOtherSessions("Bearer " + second.session().token()));
        assertTrue(sessions.findByToken(first.result().session().token()).isEmpty());
        assertEquals(user.getId(), auth.requireAdmin("Bearer " + second.session().token()).getId());
    }

    @Test
    void ordinaryLoginRequiresCaptchaAfterThreePasswordFailures() {
        String phone = "19804000" + NUMBER.incrementAndGet();
        AppUser customer = new AppUser(phone, passwords.encode(PASSWORD), "验证码用户", UserRole.CUSTOMER);
        users.saveAndFlush(customer);
        Map<String, Object> wrong = Map.of("phone", phone, "password", "wrong-password");

        for (int attempt = 0; attempt < 3; attempt++) {
            assertEquals(HttpStatus.UNAUTHORIZED,
                    http.postForEntity("/api/v1/auth/login", wrong, Map.class).getStatusCode());
        }
        ResponseEntity<Map> required = http.postForEntity("/api/v1/auth/login", wrong, Map.class);
        assertEquals(428, required.getStatusCode().value());
        ResponseEntity<Map> captcha = http.postForEntity("/api/v1/auth/captcha", Map.of(), Map.class);
        assertEquals(HttpStatus.OK, captcha.getStatusCode());
        assertTrue(((String) captcha.getBody().get("imageDataUrl")).startsWith("data:image/png;base64,iVBOR"));
    }

    @Test
    void failedAttemptsCommitAndCannotBeResetByRepeatingThePasswordStep() {
        AppUser user = admin(AdminRole.SUPPORT);
        Enrollment enrolled = enroll(user);
        for (int i = 0; i < 5; i++) {
            var challenge = mfa.begin(user.getPhone(), PASSWORD);
            assertThrows(ResponseStatusException.class, () -> mfa.verify(challenge.challengeToken(), "invalid-code"));
        }
        assertEquals(5, users.findById(user.getId()).orElseThrow().getFailedLoginAttempts());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, assertThrows(ResponseStatusException.class,
                () -> mfa.begin(user.getPhone(), PASSWORD)).getStatusCode());
        assertEquals(5, jdbc.queryForObject("select count(*) from admin_audit_logs where administrator_id = ? and action = 'ADMIN_MFA_LOGIN_FAILED'", Integer.class, user.getId()));
        assertNotNull(enrolled.result().session());
    }

    @Test
    void passwordFailuresArePersisted() {
        AppUser user = admin(AdminRole.SUPPORT);
        for (int i = 0; i < 5; i++) assertThrows(ResponseStatusException.class, () -> mfa.begin(user.getPhone(), "wrong"));
        assertEquals(5, users.findById(user.getId()).orElseThrow().getFailedLoginAttempts());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, assertThrows(ResponseStatusException.class,
                () -> mfa.begin(user.getPhone(), PASSWORD)).getStatusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"password", "role", "suspend_restore"})
    void accountChangesInvalidateOutstandingChallengesAndSessions(String change) {
        AppUser user = admin(AdminRole.SUPPORT);
        Enrollment enrolled = enroll(user);
        var challenge = mfa.begin(user.getPhone(), PASSWORD);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            AppUser locked = users.findLockedById(user.getId()).orElseThrow();
            switch (change) {
                case "password" -> locked.changePasswordHash(passwords.encode("NewPassword2026"));
                case "role" -> locked.changeAdminRole(AdminRole.REVIEWER);
                default -> { locked.suspend(); locked.restore(); }
            }
        });
        assertThrows(ResponseStatusException.class, () -> mfa.verify(challenge.challengeToken(), enrolled.result().recoveryCodes().get(0)));
        assertThrows(ResponseStatusException.class, () -> auth.requireUser("Bearer " + enrolled.result().session().token()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void simultaneousConsumptionOnlySucceedsOnceAndPreventsReplay(boolean recovery) throws Exception {
        AppUser user = admin(AdminRole.REVIEWER);
        Enrollment enrolled = enroll(user);
        var challenge = mfa.begin(user.getPhone(), PASSWORD);
        String code = recovery ? enrolled.result().recoveryCodes().get(0)
                : AdminMfaCrypto.code(enrolled.secret(), Instant.now().getEpochSecond() / 30);
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Boolean> verify = () -> {
                start.await();
                try { return mfa.verify(challenge.challengeToken(), code).session() != null; }
                catch (ResponseStatusException error) { assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode()); return false; }
            };
            Future<Boolean> first = pool.submit(verify), second = pool.submit(verify);
            start.countDown();
            assertEquals(1, (first.get(15, TimeUnit.SECONDS) ? 1 : 0) + (second.get(15, TimeUnit.SECONDS) ? 1 : 0));
        } finally { pool.shutdownNow(); }
        var retry = mfa.begin(user.getPhone(), PASSWORD);
        assertThrows(ResponseStatusException.class, () -> mfa.verify(retry.challengeToken(), code));
    }

    @Test
    void replacementRevokesOldFactorsAndSessionsAfterConfirmation() {
        AppUser user = admin(AdminRole.OPERATIONS);
        Enrollment old = enroll(user);
        AppUser authenticated = auth.requireAdmin("Bearer " + old.result().session().token());
        var replacement = mfa.replace(authenticated, PASSWORD, old.result().recoveryCodes().get(0));
        assertEquals("REPLACE", replacement.step());
        assertEquals(user.getId(), auth.requireAdmin("Bearer " + old.result().session().token()).getId());
        var next = mfa.verify(replacement.challengeToken(), AdminMfaCrypto.code(replacement.secret(), Instant.now().getEpochSecond() / 30));
        assertEquals(10, next.recoveryCodes().size());
        assertThrows(ResponseStatusException.class, () -> auth.requireAdmin("Bearer " + old.result().session().token()));
        var challenge = mfa.begin(user.getPhone(), PASSWORD);
        assertThrows(ResponseStatusException.class, () -> mfa.verify(challenge.challengeToken(), old.result().recoveryCodes().get(1)));
        assertEquals(user.getId(), auth.requireAdmin("Bearer " + next.session().token()).getId());
    }

    @Test
    void administrativeResetRequiresASeparateGrantBeforeNewEnrollment() {
        AppUser operator = admin(AdminRole.SUPER_ADMIN), target = admin(AdminRole.SUPPORT);
        Enrollment operatorLogin = enroll(operator), targetLogin = enroll(target);
        var pending = mfa.begin(target.getPhone(), PASSWORD);
        AppUser authenticated = auth.requireAdmin("Bearer " + operatorLogin.result().session().token());
        String grant = mfa.reset(authenticated, target.getId(), "当面核对本人校园证件");
        assertThrows(ResponseStatusException.class, () -> auth.requireAdmin("Bearer " + targetLogin.result().session().token()));
        assertThrows(ResponseStatusException.class, () -> mfa.verify(pending.challengeToken(), targetLogin.result().recoveryCodes().get(0)));
        var recovery = mfa.begin(target.getPhone(), PASSWORD);
        assertEquals("RECOVER", recovery.step());
        assertNull(recovery.secret());
        assertThrows(ResponseStatusException.class, () -> mfa.verify(recovery.challengeToken(), "incorrect-grant"));
        var enrollment = mfa.verify(recovery.challengeToken(), grant).challenge();
        assertEquals("ENROLL", enrollment.step());
        mfa.verify(enrollment.challengeToken(), AdminMfaCrypto.code(enrollment.secret(), Instant.now().getEpochSecond() / 30));
        assertEquals("VERIFY", mfa.begin(target.getPhone(), PASSWORD).step());
        assertThrows(ResponseStatusException.class, () -> mfa.reset(authenticated, operator.getId(), "self reset"));
    }

    @Test
    void nonSuperAdminCannotResetAnotherAccountAndExpiredGrantIsRejected() {
        AppUser operator = admin(AdminRole.OPERATIONS), target = admin(AdminRole.SUPPORT), superAdmin = admin(AdminRole.SUPER_ADMIN);
        var login = enroll(operator).result().session();
        assertThrows(ResponseStatusException.class, () -> mfa.reset(auth.requireAdmin("Bearer " + login.token()), target.getId(), "核验"));
        var superLogin = enroll(superAdmin).result().session();
        String grant = mfa.reset(auth.requireAdmin("Bearer " + superLogin.token()), target.getId(), "核验");
        jdbc.update("update admin_mfa_credentials set reset_grant_expires_at = now() - interval '1 second' where user_id = ?", target.getId());
        var challenge = mfa.begin(target.getPhone(), PASSWORD);
        assertThrows(ResponseStatusException.class, () -> mfa.verify(challenge.challengeToken(), grant));
    }

    @Test
    void offlineRecoveryIsTargetedAuditedAndStillRequiresPasswordAndGrant() throws Exception {
        AppUser target = admin(AdminRole.SUPER_ADMIN), other = admin(AdminRole.SUPER_ADMIN);
        var before = enroll(target).result().session();
        var otherSession = enroll(other).result().session();
        String grant = AdminMfaCrypto.randomToken();
        String sql = Files.readString(Path.of("deploy/admin-mfa-recovery.sql"))
                .replace(":'phone'", "'" + target.getPhone() + "'")
                .replace(":'reason'", "'QA offline verification'")
                .replace(":'grant_hash'", "'" + AdminMfaCrypto.hash(grant) + "'")
                .replace("BEGIN;", "").replace("COMMIT;", "");
        new TransactionTemplate(transactions).executeWithoutResult(status -> jdbc.execute(sql));
        assertThrows(ResponseStatusException.class, () -> auth.requireAdmin("Bearer " + before.token()));
        assertEquals(other.getId(), auth.requireAdmin("Bearer " + otherSession.token()).getId());
        assertThrows(ResponseStatusException.class, () -> mfa.begin(target.getPhone(), "wrong"));
        var recovery = mfa.begin(target.getPhone(), PASSWORD);
        assertEquals("RECOVER", recovery.step());
        assertNull(recovery.secret());
        var challenge = mfa.verify(recovery.challengeToken(), grant).challenge();
        var after = mfa.verify(challenge.challengeToken(), AdminMfaCrypto.code(challenge.secret(), Instant.now().getEpochSecond() / 30));
        assertEquals(target.getId(), auth.requireAdmin("Bearer " + after.session().token()).getId());
        assertEquals(1, jdbc.queryForObject("select count(*) from admin_audit_logs where administrator_id = ? and action = 'ADMIN_MFA_RESET'", Integer.class, target.getId()));
    }

    @Test
    void upgradeRevokesOnlyAdministratorSessions() {
        String schema = "mfa_upgrade";
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).target("16").load().migrate();
        jdbc.update("insert into mfa_upgrade.app_users (id, created_at, display_name, password_hash, phone, role, admin_role) values "
                + "(1, now(), 'QA Admin', 'unused', '19904009001', 'ADMIN', 'SUPER_ADMIN'), "
                + "(2, now(), 'QA Customer', 'unused', '19904009002', 'CUSTOMER', null)");
        jdbc.update("insert into mfa_upgrade.user_sessions (expires_at, token, user_id) values "
                + "(now() + interval '1 hour', 'old-admin', 1), (now() + interval '1 hour', 'customer', 2)");
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).load().migrate();
        assertEquals(List.of("customer"), jdbc.queryForList("select token from mfa_upgrade.user_sessions", String.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from mfa_upgrade.admin_mfa_credentials", Integer.class));
        assertEquals(0L, jdbc.queryForObject("select security_version from mfa_upgrade.app_users where id = 1", Long.class));
    }

    @Test
    void accountRateLimitRejectsRepeatedChallengesWithNoStore() throws Exception {
        AppUser user = admin(AdminRole.AUDITOR);
        Map<String, String> request = Map.of("phone", user.getPhone(), "password", PASSWORD);
        for (int i = 0; i < 10; i++) {
            assertEquals(HttpStatus.OK, http.postForEntity("/api/v1/auth/admin/login", request, Map.class).getStatusCode());
        }
        // TestRestTemplate's Apache client retries 429 responses, hiding the first rejection.
        var limited = java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(http.getRootUri() + "/api/v1/auth/admin/login"))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"phone\":\"" + user.getPhone()
                        + "\",\"password\":\"" + PASSWORD + "\"}")).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(429, limited.statusCode());
        assertEquals("no-store", limited.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals(0, jdbc.queryForObject("select count(*) from user_sessions where user_id = ?", Integer.class, user.getId()));
    }

    @Test
    void httpLoginOnlyReturnsRestrictedChallengeAndDisablesCaching() {
        AppUser user = admin(AdminRole.AUDITOR);
        var response = http.postForEntity("/api/v1/auth/admin/login", Map.of("phone", user.getPhone(), "password", PASSWORD), Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertFalse(response.getBody().containsKey("token"));
        assertFalse(response.getBody().containsKey("session"));
        HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth((String) response.getBody().get("challengeToken"));
        assertEquals(HttpStatus.UNAUTHORIZED, http.exchange("/api/v1/admin/dashboard", HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, http.postForEntity("/api/v1/auth/login", Map.of("phone", user.getPhone(), "password", PASSWORD), Map.class).getStatusCode());
        var invalid = http.postForEntity("/api/v1/auth/admin/verify", Map.of("challengeToken", "", "code", ""), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, invalid.getStatusCode());
    }

    private record Enrollment(String secret, AdminMfaService.Verification result) { }
}
