package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.dto.DispatchExperimentRequest;
import com.zhangxuan.runruncampus.dto.DispatchScenario;
import com.zhangxuan.runruncampus.dto.DispatchScenarioCandidate;
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

/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface CampusDispatchExperimentService {

    DispatchExperimentVO compare(DispatchExperimentRequest request);
}
