package com.zhangxuan.runrunv2.vo;

import com.zhangxuan.runrunv2.enums.Kind;
import java.time.OffsetDateTime;
import java.util.List;

public record FulfillmentOrderVO(String id, String orderNo, int flowVersion, String rawStatus, String displayStatus, long version, long originalFeeCents, long effectiveFeeCents, OffsetDateTime deliveredAt, OffsetDateTime confirmStartedAt, OffsetDateTime confirmDeadline) {
}
