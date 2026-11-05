package com.zhangxuan.runruncampus.dto.response.commandcenter;

import java.time.Instant;

public record Snapshot(
    Instant generatedAt, int waitMinutes, int deliveryMinutes, Orders orders, Couriers couriers) {}
