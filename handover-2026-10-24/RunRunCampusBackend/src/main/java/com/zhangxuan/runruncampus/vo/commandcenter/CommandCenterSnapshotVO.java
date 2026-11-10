package com.zhangxuan.runruncampus.vo.commandcenter;

import java.time.Instant;

/** 指挥台业务快照响应；保持现有 JSON 字段及序列化契约。 */
public record CommandCenterSnapshotVO(
        Instant generatedAt,
        int waitMinutes,
        int deliveryMinutes,
        CommandCenterOrdersVO orders,
        CommandCenterCouriersVO couriers) {}
