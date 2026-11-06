package com.zhangxuan.runruncampus.service.impl;

import com.zhangxuan.runruncampus.dto.DispatchExperimentRequest;
import com.zhangxuan.runruncampus.dto.DispatchScenario;
import com.zhangxuan.runruncampus.dto.DispatchScenarioCandidate;
import com.zhangxuan.runruncampus.exception.BusinessFailure;
import com.zhangxuan.runruncampus.model.DispatchScoreCalculator;
import com.zhangxuan.runruncampus.service.CampusDispatchExperimentService;
import com.zhangxuan.runruncampus.vo.DispatchChoiceVO;
import com.zhangxuan.runruncampus.vo.DispatchComparisonVO;
import com.zhangxuan.runruncampus.vo.DispatchExperimentVO;
import com.zhangxuan.runruncampus.vo.DispatchMetricsVO;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 校园派单策略离线比较业务实现；通过注入的业务契约及数据访问组件执行，事务边界保留在本类。 */
@Service
public class CampusDispatchExperimentServiceImpl implements CampusDispatchExperimentService {

    @Override
    public DispatchExperimentVO compare(DispatchExperimentRequest request) {
        if (request == null
                || request.scenarios() == null
                || request.scenarios().isEmpty()
                || request.scenarios().size() > 100) {
            throw invalid();
        }
        Set<String> scenarioIds = new HashSet<>();
        List<DispatchComparisonVO> comparisons =
                request.scenarios().stream()
                        .map(
                                scenario -> {
                                    validate(scenario);
                                    if (!scenarioIds.add(scenario.scenarioId())) {
                                        throw invalid();
                                    }
                                    DispatchScenarioCandidate baseline =
                                            scenario.candidates().stream()
                                                    .min(baselineOrder())
                                                    .orElseThrow();
                                    DispatchScenarioCandidate proposed =
                                            scenario.candidates().stream()
                                                    .min(
                                                            experimentOrder(
                                                                    scenario.deadlineMinutes()))
                                                    .orElseThrow();
                                    return new DispatchComparisonVO(
                                            scenario.scenarioId(),
                                            choice(baseline, scenario.deadlineMinutes()),
                                            choice(proposed, scenario.deadlineMinutes()));
                                })
                        .toList();
        return new DispatchExperimentVO(
                "SIMULATION_ONLY",
                "BASELINE_V1",
                "CAMPUS_ETA_V1",
                comparisons,
                new DispatchMetricsVO(
                        comparisons.size(),
                        comparisons.stream()
                                .filter(
                                        item ->
                                                item.baseline().courierId()
                                                        != item.proposed().courierId())
                                .count(),
                        comparisons.stream()
                                .filter(item -> item.baseline().estimatedLate())
                                .count(),
                        comparisons.stream()
                                .filter(item -> item.proposed().estimatedLate())
                                .count(),
                        comparisons.stream()
                                .mapToLong(item -> item.baseline().estimatedArrivalMinutes())
                                .sum(),
                        comparisons.stream()
                                .mapToLong(item -> item.proposed().estimatedArrivalMinutes())
                                .sum()));
    }

    private void validate(DispatchScenario scenario) {
        if (scenario == null
                || scenario.scenarioId() == null
                || !scenario.scenarioId().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
                || scenario.deadlineMinutes() < 0
                || scenario.deadlineMinutes() > 1440
                || scenario.candidates() == null
                || scenario.candidates().isEmpty()
                || scenario.candidates().size() > 50) {
            throw invalid();
        }
        Set<Long> ids = new HashSet<>();
        for (DispatchScenarioCandidate candidate : scenario.candidates()) {
            if (candidate == null
                    || candidate.courierId() <= 0
                    || !ids.add(candidate.courierId())
                    || candidate.activeOrders() < 0
                    || candidate.activeOrders() > 100
                    || candidate.onlineMinutes() < 0
                    || candidate.completedOrders() < 0
                    || candidate.matchedCompletedOrders() < 0
                    || candidate.ratingCount() < 0
                    || candidate.matchedCompletedOrders() > candidate.completedOrders()
                    || candidate.walkingMinutes() < 0
                    || candidate.walkingMinutes() > 1440
                    || candidate.remainingWorkMinutes() < 0
                    || candidate.remainingWorkMinutes() > 1440
                    || candidate.averageRating() != null
                            && (!Double.isFinite(candidate.averageRating())
                                    || candidate.averageRating() < 1
                                    || candidate.averageRating() > 5)) {
                throw invalid();
            }
        }
    }

    private Comparator<DispatchScenarioCandidate> baselineOrder() {
        return Comparator.comparingInt(this::score)
                .reversed()
                .thenComparingLong(DispatchScenarioCandidate::activeOrders)
                .thenComparing(
                        Comparator.comparingLong(DispatchScenarioCandidate::onlineMinutes)
                                .reversed())
                .thenComparingLong(DispatchScenarioCandidate::courierId);
    }

    private Comparator<DispatchScenarioCandidate> experimentOrder(int deadlineMinutes) {
        return Comparator.comparing(
                        (DispatchScenarioCandidate candidate) ->
                                arrival(candidate) > deadlineMinutes)
                .thenComparingLong(this::arrival)
                .thenComparing(baselineOrder());
    }

    private DispatchChoiceVO choice(DispatchScenarioCandidate candidate, int deadlineMinutes) {
        long arrivalMinutes = arrival(candidate);
        return new DispatchChoiceVO(
                candidate.courierId(),
                score(candidate),
                arrivalMinutes,
                arrivalMinutes > deadlineMinutes);
    }

    private long arrival(DispatchScenarioCandidate candidate) {
        return (long) candidate.walkingMinutes() + candidate.remainingWorkMinutes();
    }

    private int score(DispatchScenarioCandidate candidate) {
        return DispatchScoreCalculator.recommendationScore(
                candidate.activeOrders(),
                candidate.onlineMinutes(),
                candidate.completedOrders(),
                candidate.matchedCompletedOrders(),
                candidate.averageRating(),
                candidate.ratingCount());
    }

    private BusinessFailure invalid() {
        return new BusinessFailure(400, "实验场景字段无效或超过数量限制");
    }
}
