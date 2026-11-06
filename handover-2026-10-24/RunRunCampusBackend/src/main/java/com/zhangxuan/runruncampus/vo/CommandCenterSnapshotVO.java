package com.zhangxuan.runruncampus.vo;

import java.time.Instant;

public record CommandCenterSnapshotVO(Instant generatedAt, int waitMinutes, int deliveryMinutes, CommandCenterOrdersVO orders, CommandCenterCouriersVO couriers) {
}
