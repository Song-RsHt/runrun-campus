package com.zhangxuan.runruncampus.vo.provenance;

/** 履约来源及财务核验汇总响应；保持现有 JSON 字段及序列化契约。 */
public record FulfillmentProvenanceSummaryVO(
        long recordedCount,
        long verifiedCount,
        long verifiedRevenueCount,
        Long verifiedRevenueCents) {}
