package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.vo.TaskReleaseVO;

/** 骑手无法履约上报业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface CourierReassignmentService {

    /**
     * 上报无法履约并按原有规则释放或转入人工处理。
     *
     * @param courier 当前骑手账号或标识
     * @param orderId 订单标识
     * @param reason 业务操作理由，按原有长度和必填规则校验
     * @return 任务释放
     */
    TaskReleaseVO reportUnable(AppUser courier, Long orderId, String reason);
}
