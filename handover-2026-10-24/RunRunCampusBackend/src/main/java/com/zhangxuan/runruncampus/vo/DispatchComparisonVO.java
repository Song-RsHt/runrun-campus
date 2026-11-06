package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.dto.DispatchExperimentRequest;
import com.zhangxuan.runruncampus.dto.DispatchScenario;
import com.zhangxuan.runruncampus.dto.DispatchScenarioCandidate;
import com.zhangxuan.runruncampus.model.DispatchScoreCalculator;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

public record DispatchComparisonVO(String scenarioId, DispatchChoiceVO baseline, DispatchChoiceVO proposed) {
}
