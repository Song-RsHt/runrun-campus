package com.zhangxuan.runrunv2.vo.fulfillment;

import java.time.OffsetDateTime;

/** 位置事件响应；保持现有 JSON 字段及序列化契约。 */
public record LocationEventVO(
        String eventId, OffsetDateTime receivedAt, boolean accepted, long orderVersion) {}
