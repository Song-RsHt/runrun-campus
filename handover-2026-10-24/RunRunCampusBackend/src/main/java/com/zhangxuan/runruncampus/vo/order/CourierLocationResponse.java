package com.zhangxuan.runruncampus.vo.order;

import java.time.Instant;

/** 骑手位置响应；保持现有 JSON 字段及序列化契约。 */
public record CourierLocationResponse(
        boolean available, Double latitude, Double longitude, Instant updatedAt) {}
