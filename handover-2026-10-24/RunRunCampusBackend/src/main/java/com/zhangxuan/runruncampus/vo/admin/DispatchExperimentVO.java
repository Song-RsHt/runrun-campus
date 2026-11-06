package com.zhangxuan.runruncampus.vo.admin;

import java.util.List;

/** 派单实验响应；保持现有 JSON 字段及序列化契约。 */
public record DispatchExperimentVO(
        String evidenceLevel,
        String baselineAlgorithm,
        String proposedAlgorithm,
        List<DispatchComparisonVO> comparisons,
        DispatchMetricsVO metrics) {}
