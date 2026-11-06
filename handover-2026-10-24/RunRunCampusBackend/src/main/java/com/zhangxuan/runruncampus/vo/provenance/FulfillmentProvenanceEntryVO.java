package com.zhangxuan.runruncampus.vo.provenance;

import com.zhangxuan.runruncampus.enums.provenance.Channel;

import java.time.Instant;

/** 履约来源及财务核验记录响应；保持现有 JSON 字段及序列化契约。 */
public record FulfillmentProvenanceEntryVO(
        long id,
        String sourceRef,
        Channel channel,
        Long appOrderId,
        Instant completedAt,
        Long customerPaidCents,
        Long platformRevenueCents,
        Long courierSettlementCents,
        String completionEvidenceSha256,
        String financialEvidenceSha256,
        long recordedBy,
        Instant recordedAt,
        Long verifiedBy,
        Instant verifiedAt) {}
