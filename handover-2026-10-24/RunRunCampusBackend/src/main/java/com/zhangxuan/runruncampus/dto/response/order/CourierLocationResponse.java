package com.zhangxuan.runruncampus.dto.response.order;

import java.time.Instant;

public record CourierLocationResponse(
    boolean available, Double latitude, Double longitude, Instant updatedAt) {}
