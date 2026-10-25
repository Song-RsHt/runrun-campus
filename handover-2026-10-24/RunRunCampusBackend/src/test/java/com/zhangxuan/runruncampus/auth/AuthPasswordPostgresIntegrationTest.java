package com.zhangxuan.runruncampus.auth;

import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRepository;
import com.zhangxuan.runruncampus.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.proof-storage.local-directory=target/test-password-proofs")
@ActiveProfiles("test")
@Testcontainers
class AuthPasswordPostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired SessionRepository sessions;
    @Autowired PasswordEncoder passwords;
    @Autowired TestRestTemplate http;

    @Test
    void passwordChangeRequiresCurrentPasswordAndInvalidatesEveryDevice() {
        String phone = "13800000998";
        AppUser user = users.saveAndFlush(new AppUser(phone, passwords.encode("old-password"), "测试用户", UserRole.CUSTOMER));
        Session current = auth.login(phone, "old-password");
        Session other = auth.login(phone, "old-password");

        assertEquals(HttpStatus.BAD_REQUEST, change(current, "wrong-password", "new-password"));
        assertEquals(HttpStatus.BAD_REQUEST, change(current, "old-password", "old-password"));
        assertEquals(2, sessions.findByUserAndExpiresAtAfterOrderByCreatedAtDesc(user, java.time.Instant.now()).size());

        assertEquals(HttpStatus.NO_CONTENT, change(current, "old-password", "new-password"));
        assertEquals(0, sessions.findByUserAndExpiresAtAfterOrderByCreatedAtDesc(user, java.time.Instant.now()).size());
        assertThrows(ResponseStatusException.class, () -> auth.requireUser("Bearer " + current.getToken()));
        assertThrows(ResponseStatusException.class, () -> auth.requireUser("Bearer " + other.getToken()));
        assertThrows(ResponseStatusException.class, () -> auth.login(phone, "old-password"));
        assertEquals(user.getId(), auth.login(phone, "new-password").getUser().getId());
    }

    private HttpStatus change(Session session, String currentPassword, String newPassword) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(session.getToken());
        return (HttpStatus) http.exchange("/api/v1/auth/password", HttpMethod.POST,
                new HttpEntity<>(Map.of("currentPassword", currentPassword, "newPassword", newPassword), headers),
                Void.class).getStatusCode();
    }
}
