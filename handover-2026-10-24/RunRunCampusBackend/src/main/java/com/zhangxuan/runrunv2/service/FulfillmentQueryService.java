package com.zhangxuan.runrunv2.service;

import com.zhangxuan.runrunv2.vo.CourierTaskVO;
import com.zhangxuan.runrunv2.vo.CursorPageVO;
import com.zhangxuan.runrunv2.vo.FulfillmentOrderVO;























/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface FulfillmentQueryService {

    CursorPageVO<CourierTaskVO> tasks(String token, String cursor, Integer requestedLimit, String status);

    CursorPageVO<FulfillmentOrderVO> pool(String token, String cursor, Integer requestedLimit);
}
