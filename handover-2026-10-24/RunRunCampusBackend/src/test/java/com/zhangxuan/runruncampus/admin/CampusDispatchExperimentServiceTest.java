package com.zhangxuan.runruncampus.admin;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CampusDispatchExperimentServiceTest {
    private final CampusDispatchExperimentService service = new CampusDispatchExperimentService();

    @Test
    void comparesCurrentScoringWithEstimatedArrivalWithoutDispatching() {
        var experienced = new CampusDispatchExperimentService.Candidate(10, 0, 120, 40, 20,
                5.0, 10, 18, 0);
        var nearby = new CampusDispatchExperimentService.Candidate(20, 0, 0, 0, 0,
                null, 0, 5, 0);
        var scenario = new CampusDispatchExperimentService.Scenario("synthetic-1", 10,
                List.of(experienced, nearby));

        var result = service.compare(new CampusDispatchExperimentService.Request(List.of(scenario)));

        assertEquals("SIMULATION_ONLY", result.evidenceLevel());
        assertEquals(10, result.comparisons().get(0).baseline().courierId());
        assertEquals(20, result.comparisons().get(0).proposed().courierId());
        assertEquals(1, result.metrics().changedChoices());
        assertEquals(1, result.metrics().baselineEstimatedLateCount());
        assertEquals(0, result.metrics().proposedEstimatedLateCount());
        assertEquals(18, result.metrics().baselineArrivalMinutesTotal());
        assertEquals(5, result.metrics().proposedArrivalMinutesTotal());
    }

    @Test
    void tiesAreDeterministicAndUseCourierId() {
        var first = new CampusDispatchExperimentService.Candidate(2, 0, 0, 0, 0,
                null, 0, 5, 0);
        var second = new CampusDispatchExperimentService.Candidate(1, 0, 0, 0, 0,
                null, 0, 5, 0);
        var scenario = new CampusDispatchExperimentService.Scenario("tie", 5, List.of(first, second));

        var result = service.compare(new CampusDispatchExperimentService.Request(List.of(scenario)));

        assertEquals(1, result.comparisons().get(0).baseline().courierId());
        assertEquals(1, result.comparisons().get(0).proposed().courierId());
    }

    @Test
    void rejectsDuplicateCandidates() {
        var candidate = new CampusDispatchExperimentService.Candidate(1, 0, 0, 0, 0,
                null, 0, 5, 0);
        var scenario = new CampusDispatchExperimentService.Scenario("duplicate", 5,
                List.of(candidate, candidate));

        var exception = assertThrows(ResponseStatusException.class,
                () -> service.compare(new CampusDispatchExperimentService.Request(List.of(scenario))));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
}
