package com.zhangxuan.runruncampus.dto.response.commandcenter;

import java.time.Instant;

public record OrderItem(
    long id,
    String orderNo,
    String status,
    String destination,
    String courierName,
    Instant stageStart,
    boolean estimated) {}
