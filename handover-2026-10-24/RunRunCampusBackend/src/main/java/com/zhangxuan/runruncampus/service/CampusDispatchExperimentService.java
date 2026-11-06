package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.dto.DispatchExperimentRequest;
import com.zhangxuan.runruncampus.vo.DispatchExperimentVO;

/** 校园派单策略离线比较业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface CampusDispatchExperimentService {

    /**
     * 对同一批离线场景比较派单策略，不修改真实订单。
     *
     * @param request 派单实验
     * @return 派单实验
     */
    DispatchExperimentVO compare(DispatchExperimentRequest request);
}
