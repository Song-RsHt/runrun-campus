package com.zhangxuan.runrunv2.vo;

import com.zhangxuan.runrunv2.enums.Kind;
import java.time.OffsetDateTime;
import java.util.List;

public record FulfillmentTaskVO(String id, String orderId, String courierId, String status, long version, OffsetDateTime createdAt) {
}
