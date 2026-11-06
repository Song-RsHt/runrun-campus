package com.zhangxuan.runruncampus.vo;















public record DispatchMetricsVO(int scenarioCount, long changedChoices, long baselineEstimatedLateCount, long proposedEstimatedLateCount, long baselineArrivalMinutesTotal, long proposedArrivalMinutesTotal) {
}
