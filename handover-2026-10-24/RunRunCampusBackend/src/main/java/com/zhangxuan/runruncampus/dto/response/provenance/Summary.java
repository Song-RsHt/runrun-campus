package com.zhangxuan.runruncampus.dto.response.provenance;

public record Summary(
    long recordedCount, long verifiedCount, long verifiedRevenueCount, Long verifiedRevenueCents) {}
