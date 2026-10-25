package com.zhangxuan.runruncampus.api;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

@Service
public class ApiRateLimitService {
    private static final long REFILL_MILLIS = 60_000;
    private static final long STALE_BUCKET_MILLIS = 10 * 60_000;
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Clock clock;
    private final Map<String, Bucket> buckets = new HashMap<>();

    public ApiRateLimitService() {
        this(Clock.systemUTC());
    }

    ApiRateLimitService(Clock clock) {
        this.clock = clock;
    }

    public boolean tryAcquire(String scope, String identity, int capacityPerMinute) {
        if (capacityPerMinute <= 0) throw new IllegalArgumentException("限流容量必须大于 0");
        String key = scope + ":" + fingerprint(identity);
        return acquire(key, capacityPerMinute, clock.millis());
    }

    private synchronized boolean acquire(String key, int capacity, long now) {
        if (buckets.size() >= CLEANUP_THRESHOLD) {
            buckets.entrySet().removeIf(entry -> now - entry.getValue().lastSeen >= STALE_BUCKET_MILLIS);
        }

        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(capacity, now));
        long elapsed = Math.max(0, now - bucket.lastRefill);
        bucket.tokens = Math.min(capacity, bucket.tokens + elapsed * capacity / (double) REFILL_MILLIS);
        bucket.lastRefill = now;
        bucket.lastSeen = now;
        if (bucket.tokens < 1) return false;
        bucket.tokens -= 1;
        return true;
    }

    static String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
    }

    private static final class Bucket {
        private double tokens;
        private long lastRefill;
        private long lastSeen;

        private Bucket(int capacity, long now) {
            this.tokens = capacity;
            this.lastRefill = now;
            this.lastSeen = now;
        }
    }
}
