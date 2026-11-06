package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.vo.DispatchCandidatesVO;
import com.zhangxuan.runruncampus.vo.DispatchResultVO;

/** 管理员派单业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface AdminDispatchService {

    /**
     * 列出订单的可用骑手候选，保持既有评分与排序规则。
     *
     * @param orderId 订单标识
     * @param requestedPage 调用方请求的分页页码
     * @return 派单候选集
     */
    DispatchCandidatesVO candidates(Long orderId, int requestedPage);

    /**
     * 将指定订单派给骑手，校验订单状态和骑手可用性。
     *
     * @param administrator 已通过权限校验的管理员
     * @param orderId 订单标识
     * @param courierId 目标骑手标识
     * @return 派单结果
     */
    DispatchResultVO dispatch(AppUser administrator, Long orderId, Long courierId);
}
