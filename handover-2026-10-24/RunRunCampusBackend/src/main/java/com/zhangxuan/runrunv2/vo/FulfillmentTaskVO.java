package com.zhangxuan.runrunv2.vo;

import java.time.OffsetDateTime;






public record FulfillmentTaskVO(String id, String orderId, String courierId, String status, long version, OffsetDateTime createdAt) {
}
