package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.Channel;
import java.time.Instant;

public record FulfillmentProvenanceEntryVO(long id, String sourceRef, Channel channel, Long appOrderId, Instant completedAt, Long customerPaidCents, Long platformRevenueCents, Long courierSettlementCents, String completionEvidenceSha256, String financialEvidenceSha256, long recordedBy, Instant recordedAt, Long verifiedBy, Instant verifiedAt) {
}
