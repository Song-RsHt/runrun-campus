package com.zhangxuan.runruncampus.vo.admin;

/** 派单对比响应；保持现有 JSON 字段及序列化契约。 */
public record DispatchComparisonVO(
        String scenarioId, DispatchChoiceVO baseline, DispatchChoiceVO proposed) {}
