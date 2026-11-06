package com.zhangxuan.runruncampus.vo;

import java.util.List;














public record DispatchExperimentVO(String evidenceLevel, String baselineAlgorithm, String proposedAlgorithm, List<DispatchComparisonVO> comparisons, DispatchMetricsVO metrics) {
}
