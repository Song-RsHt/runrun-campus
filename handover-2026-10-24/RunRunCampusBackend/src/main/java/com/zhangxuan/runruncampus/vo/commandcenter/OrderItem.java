package com.zhangxuan.runruncampus.vo.commandcenter;

import java.time.Instant;

/** 订单创建与状态管理条目响应；保持现有 JSON 字段及序列化契约。 */
public record OrderItem(
        long id,
        String orderNo,
        String status,
        String destination,
        String courierName,
        Instant stageStart,
        boolean estimated) {}
