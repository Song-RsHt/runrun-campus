package com.zhangxuan.runruncampus.dto.response.commandcenter;

import java.time.Instant;

public record CourierItem(long id, String displayName, long activeOrders, Instant onlineSince) {}
