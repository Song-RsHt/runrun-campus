package com.zhangxuan.runruncampus.vo;

/** 派单选择响应；保持现有 JSON 字段及序列化契约。 */
public record DispatchChoiceVO(
        long courierId, int baselineScore, long estimatedArrivalMinutes, boolean estimatedLate) {}
