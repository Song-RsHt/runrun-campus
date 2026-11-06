package com.zhangxuan.runruncampus.vo;

import java.time.Instant;




public record Snapshot(Instant generatedAt, int waitMinutes, int deliveryMinutes, Orders orders, Couriers couriers) {
}
