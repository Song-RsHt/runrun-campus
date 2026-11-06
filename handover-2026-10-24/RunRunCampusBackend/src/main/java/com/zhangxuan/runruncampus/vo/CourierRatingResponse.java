package com.zhangxuan.runruncampus.vo;

/** 骑手评价响应；保持现有 JSON 字段及序列化契约。 */
public record CourierRatingResponse(double averageScore, long ratingCount) {}
