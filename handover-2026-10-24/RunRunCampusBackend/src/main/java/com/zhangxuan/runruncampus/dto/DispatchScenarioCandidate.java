package com.zhangxuan.runruncampus.dto;

import com.zhangxuan.runruncampus.model.DispatchScoreCalculator;
import com.zhangxuan.runruncampus.vo.DispatchChoiceVO;
import com.zhangxuan.runruncampus.vo.DispatchComparisonVO;
import com.zhangxuan.runruncampus.vo.DispatchExperimentVO;
import com.zhangxuan.runruncampus.vo.DispatchMetricsVO;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

public record DispatchScenarioCandidate(long courierId, long activeOrders, long onlineMinutes, long completedOrders, long matchedCompletedOrders, Double averageRating, long ratingCount, int walkingMinutes, int remainingWorkMinutes) {
}
