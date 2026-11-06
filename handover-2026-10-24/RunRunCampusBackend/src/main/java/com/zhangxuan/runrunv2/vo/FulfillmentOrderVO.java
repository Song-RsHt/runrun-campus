package com.zhangxuan.runrunv2.vo;

import java.time.OffsetDateTime;






public record FulfillmentOrderVO(String id, String orderNo, int flowVersion, String rawStatus, String displayStatus, long version, long originalFeeCents, long effectiveFeeCents, OffsetDateTime deliveredAt, OffsetDateTime confirmStartedAt, OffsetDateTime confirmDeadline) {
}
