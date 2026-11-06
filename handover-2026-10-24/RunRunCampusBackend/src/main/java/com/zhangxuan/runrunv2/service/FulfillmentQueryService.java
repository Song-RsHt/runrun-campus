package com.zhangxuan.runrunv2.service;

import com.zhangxuan.runrunv2.vo.CourierTaskVO;
import com.zhangxuan.runrunv2.vo.CursorPageVO;
import com.zhangxuan.runrunv2.vo.FulfillmentOrderVO;

/** 骑手任务与任务池查询业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface FulfillmentQueryService {

    /**
     * 用稳定游标读取骑手当前及历史任务。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param cursor 上页返回的稳定分页游标
     * @param requestedLimit 请求的游标分页容量，按现有上限校验
     * @param status 可选业务状态筛选
     * @return 骑手任务页及下一页稳定游标
     */
    CursorPageVO<CourierTaskVO> tasks(
            String token, String cursor, Integer requestedLimit, String status);

    /**
     * 用稳定游标读取有权接单骑手的可领取任务池。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param cursor 上页返回的稳定分页游标
     * @param requestedLimit 请求的游标分页容量，按现有上限校验
     * @return 可领取订单页及下一页稳定游标
     */
    CursorPageVO<FulfillmentOrderVO> pool(String token, String cursor, Integer requestedLimit);
}
