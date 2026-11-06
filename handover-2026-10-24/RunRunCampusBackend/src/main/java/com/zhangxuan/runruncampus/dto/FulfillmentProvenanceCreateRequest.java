package com.zhangxuan.runruncampus.dto;

import com.zhangxuan.runruncampus.enums.Channel;
import java.time.Instant;

public record FulfillmentProvenanceCreateRequest(String sourceRef, Channel channel, Long appOrderId, Instant completedAt, Long customerPaidCents, Long platformRevenueCents, Long courierSettlementCents, String completionEvidenceSha256, String financialEvidenceSha256) {
}
