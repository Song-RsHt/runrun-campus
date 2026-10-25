package com.zhangxuan.runruncampus.api;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiRateLimitServiceTest {
    @Test
    void tokenBucketRejectsExcessAndRefillsContinuously() {
        MutableClock clock = new MutableClock();
        ApiRateLimitService service = new ApiRateLimitService(clock);

        assertTrue(service.tryAcquire("write", "session-a", 2));
        assertTrue(service.tryAcquire("write", "session-a", 2));
        assertFalse(service.tryAcquire("write", "session-a", 2));

        clock.advance(Duration.ofSeconds(30));

        assertTrue(service.tryAcquire("write", "session-a", 2));
        assertFalse(service.tryAcquire("write", "session-a", 2));
    }

    @Test
    void differentIdentitiesAndScopesUseIndependentBuckets() {
        ApiRateLimitService service = new ApiRateLimitService(new MutableClock());

        assertTrue(service.tryAcquire("proof", "session-a", 1));
        assertFalse(service.tryAcquire("proof", "session-a", 1));
        assertTrue(service.tryAcquire("proof", "session-b", 1));
        assertTrue(service.tryAcquire("read", "session-a", 1));
    }

    @Test
    void trackedIdentityIsOneWayFingerprint() {
        String rawToken = "Bearer secret-session-token";

        String fingerprint = ApiRateLimitService.fingerprint(rawToken);

        assertNotEquals(rawToken, fingerprint);
        assertFalse(fingerprint.contains("secret-session-token"));
        assertTrue(fingerprint.matches("[0-9a-f]{64}"));
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-09-02T00:00:00Z");

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
