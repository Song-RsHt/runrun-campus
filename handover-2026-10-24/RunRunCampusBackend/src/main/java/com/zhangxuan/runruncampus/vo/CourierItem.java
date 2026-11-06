package com.zhangxuan.runruncampus.vo;

import java.time.Instant;

/** 骑手条目响应；保持现有 JSON 字段及序列化契约。 */
public record CourierItem(long id, String displayName, long activeOrders, Instant onlineSince) {}
