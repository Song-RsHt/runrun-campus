package com.zhangxuan.runruncampus.dto.provenance;

import com.zhangxuan.runruncampus.enums.provenance.Channel;

import java.time.Instant;

/** 履约来源及财务凭证登记创建请求参数；保留入口的字段、构造器及校验契约。 */
public record FulfillmentProvenanceCreateRequest(
        String sourceRef,
        Channel channel,
        Long appOrderId,
        Instant completedAt,
        Long customerPaidCents,
        Long platformRevenueCents,
        Long courierSettlementCents,
        String completionEvidenceSha256,
        String financialEvidenceSha256) {}
