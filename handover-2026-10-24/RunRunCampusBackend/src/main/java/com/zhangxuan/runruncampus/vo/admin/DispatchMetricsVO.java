package com.zhangxuan.runruncampus.vo.admin;

/** 派单指标响应；保持现有 JSON 字段及序列化契约。 */
public record DispatchMetricsVO(
        int scenarioCount,
        long changedChoices,
        long baselineEstimatedLateCount,
        long proposedEstimatedLateCount,
        long baselineArrivalMinutesTotal,
        long proposedArrivalMinutesTotal) {}
