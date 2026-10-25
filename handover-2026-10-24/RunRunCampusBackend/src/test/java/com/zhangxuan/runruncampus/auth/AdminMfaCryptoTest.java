package com.zhangxuan.runruncampus.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class AdminMfaCryptoTest {
    private static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    private static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @ParameterizedTest
    @CsvSource({"59,287082", "1111111109,081804", "1111111111,050471", "1234567890,005924", "2000000000,279037", "20000000000,353130"})
    void matchesRfc6238Sha1VectorsTruncatedToSixDigits(long seconds, String expected) {
        assertEquals(expected, AdminMfaCrypto.code(SECRET, seconds / 30));
    }

    @Test
    void restrictsWindowAndRejectsUsedTimeSteps() {
        for (long step = 99; step <= 101; step++) {
            String code = AdminMfaCrypto.code(SECRET, step);
            assertEquals(step, AdminMfaCrypto.matchStep(SECRET, code, 100, -1));
            assertEquals(-1, AdminMfaCrypto.matchStep(SECRET, code, 100, step));
        }
        assertEquals(-1, AdminMfaCrypto.matchStep(SECRET, AdminMfaCrypto.code(SECRET, 98), 100, -1));
        assertEquals(-1, AdminMfaCrypto.matchStep(SECRET, "12345", 100, -1));
    }

    @Test
    void encryptsWithUniqueIvAndAccountBinding() {
        AdminMfaCrypto crypto = new AdminMfaCrypto(KEY, new MockEnvironment());
        String first = crypto.encrypt(1L, SECRET), second = crypto.encrypt(1L, SECRET);
        assertNotEquals(first, second);
        assertFalse(first.contains(SECRET));
        assertEquals(SECRET, crypto.decrypt(1L, first));
        assertThrows(ResponseStatusException.class, () -> crypto.decrypt(2L, first));
        byte[] tampered = Base64.getDecoder().decode(first); tampered[tampered.length - 1] ^= 1;
        assertThrows(ResponseStatusException.class, () -> crypto.decrypt(1L, Base64.getEncoder().encodeToString(tampered)));
        String otherKey = Base64.getEncoder().encodeToString("abcdef0123456789abcdef0123456789".getBytes(StandardCharsets.UTF_8));
        assertThrows(ResponseStatusException.class, () -> new AdminMfaCrypto(otherKey, new MockEnvironment()).decrypt(1L, first));
    }

    @Test
    void failsClosedWhenConfigurationIsMissingOrInvalid() {
        assertThrows(ResponseStatusException.class, () -> new AdminMfaCrypto("", new MockEnvironment()).requireConfigured());
        assertThrows(IllegalStateException.class, () -> new AdminMfaCrypto("", new MockEnvironment().withProperty("spring.profiles.active", "prod")));
        assertThrows(IllegalStateException.class, () -> new AdminMfaCrypto("not-base64", new MockEnvironment()));
        assertThrows(IllegalStateException.class, () -> new AdminMfaCrypto(Base64.getEncoder().encodeToString(new byte[32]), new MockEnvironment()));
    }
}
