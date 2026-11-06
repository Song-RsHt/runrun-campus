package com.zhangxuan.runrunv2.vo.fulfillment;

import java.time.OffsetDateTime;
import java.util.List;

/** 骑手任务响应；保持现有 JSON 字段及序列化契约。 */
public record CourierTaskVO(
        String taskId,
        String taskStatus,
        long taskVersion,
        int flowVersion,
        String orderId,
        String orderNo,
        String orderStatus,
        long orderVersion,
        OffsetDateTime createdAt,
        List<String> allowedActions) {}
