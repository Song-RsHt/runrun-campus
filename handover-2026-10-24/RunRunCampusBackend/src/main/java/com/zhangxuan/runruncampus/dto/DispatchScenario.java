package com.zhangxuan.runruncampus.dto;

import java.util.List;















public record DispatchScenario(String scenarioId, int deadlineMinutes, List<DispatchScenarioCandidate> candidates) {
}
