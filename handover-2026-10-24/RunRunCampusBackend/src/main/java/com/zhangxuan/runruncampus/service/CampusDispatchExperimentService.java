package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.dto.DispatchExperimentRequest;
import com.zhangxuan.runruncampus.vo.DispatchExperimentVO;


















/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface CampusDispatchExperimentService {

    DispatchExperimentVO compare(DispatchExperimentRequest request);
}
