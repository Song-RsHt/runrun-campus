package com.zhangxuan.runruncampus.admin;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class CampusDispatchExperimentService {
    public Result compare(Request request) {
        if (request == null || request.scenarios() == null || request.scenarios().isEmpty()
                || request.scenarios().size() > 100) {
            throw invalid();
        }
        Set<String> scenarioIds = new HashSet<>();
        List<Comparison> comparisons = request.scenarios().stream().map(scenario -> {
            validate(scenario);
            if (!scenarioIds.add(scenario.scenarioId())) throw invalid();
            Candidate baseline = scenario.candidates().stream().min(baselineOrder()).orElseThrow();
            Candidate proposed = scenario.candidates().stream().min(experimentOrder(scenario.deadlineMinutes()))
                    .orElseThrow();
            return new Comparison(scenario.scenarioId(), choice(baseline, scenario.deadlineMinutes()),
                    choice(proposed, scenario.deadlineMinutes()));
        }).toList();
        return new Result("SIMULATION_ONLY", "BASELINE_V1", "CAMPUS_ETA_V1", comparisons,
                new Metrics(comparisons.size(),
                        comparisons.stream().filter(item -> item.baseline().courierId()
                                != item.proposed().courierId()).count(),
                        comparisons.stream().filter(item -> item.baseline().estimatedLate()).count(),
                        comparisons.stream().filter(item -> item.proposed().estimatedLate()).count(),
                        comparisons.stream().mapToLong(item -> item.baseline().estimatedArrivalMinutes()).sum(),
                        comparisons.stream().mapToLong(item -> item.proposed().estimatedArrivalMinutes()).sum()));
    }

    private void validate(Scenario scenario) {
        if (scenario == null || scenario.scenarioId() == null
                || !scenario.scenarioId().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
                || scenario.deadlineMinutes() < 0 || scenario.deadlineMinutes() > 1440
                || scenario.candidates() == null || scenario.candidates().isEmpty()
                || scenario.candidates().size() > 50) throw invalid();
        Set<Long> ids = new HashSet<>();
        for (Candidate candidate : scenario.candidates()) {
            if (candidate == null || candidate.courierId() <= 0 || !ids.add(candidate.courierId())
                    || candidate.activeOrders() < 0 || candidate.activeOrders() > 100
                    || candidate.onlineMinutes() < 0 || candidate.completedOrders() < 0
                    || candidate.matchedCompletedOrders() < 0 || candidate.ratingCount() < 0
                    || candidate.matchedCompletedOrders() > candidate.completedOrders()
                    || candidate.walkingMinutes() < 0 || candidate.walkingMinutes() > 1440
                    || candidate.remainingWorkMinutes() < 0 || candidate.remainingWorkMinutes() > 1440
                    || candidate.averageRating() != null && (!Double.isFinite(candidate.averageRating())
                    || candidate.averageRating() < 1 || candidate.averageRating() > 5)) throw invalid();
        }
    }

    private Comparator<Candidate> baselineOrder() {
        return Comparator.comparingInt(this::score).reversed()
                .thenComparingLong(Candidate::activeOrders)
                .thenComparing(Comparator.comparingLong(Candidate::onlineMinutes).reversed())
                .thenComparingLong(Candidate::courierId);
    }

    private Comparator<Candidate> experimentOrder(int deadlineMinutes) {
        return Comparator.comparing((Candidate candidate) -> arrival(candidate) > deadlineMinutes)
                .thenComparingLong(this::arrival)
                .thenComparing(baselineOrder());
    }

    private Choice choice(Candidate candidate, int deadlineMinutes) {
        long arrivalMinutes = arrival(candidate);
        return new Choice(candidate.courierId(), score(candidate), arrivalMinutes,
                arrivalMinutes > deadlineMinutes);
    }

    private long arrival(Candidate candidate) {
        return (long) candidate.walkingMinutes() + candidate.remainingWorkMinutes();
    }

    private int score(Candidate candidate) {
        return AdminDispatchService.recommendationScore(candidate.activeOrders(), candidate.onlineMinutes(),
                candidate.completedOrders(), candidate.matchedCompletedOrders(), candidate.averageRating(),
                candidate.ratingCount());
    }

    private ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "实验场景字段无效或超过数量限制");
    }

    public record Request(List<Scenario> scenarios) { }
    public record Scenario(String scenarioId, int deadlineMinutes, List<Candidate> candidates) { }
    public record Candidate(long courierId, long activeOrders, long onlineMinutes, long completedOrders,
                            long matchedCompletedOrders, Double averageRating, long ratingCount,
                            int walkingMinutes, int remainingWorkMinutes) { }
    public record Choice(long courierId, int baselineScore, long estimatedArrivalMinutes, boolean estimatedLate) { }
    public record Comparison(String scenarioId, Choice baseline, Choice proposed) { }
    public record Metrics(int scenarioCount, long changedChoices, long baselineEstimatedLateCount,
                          long proposedEstimatedLateCount, long baselineArrivalMinutesTotal,
                          long proposedArrivalMinutesTotal) { }
    public record Result(String evidenceLevel, String baselineAlgorithm, String proposedAlgorithm,
                         List<Comparison> comparisons, Metrics metrics) { }
}
