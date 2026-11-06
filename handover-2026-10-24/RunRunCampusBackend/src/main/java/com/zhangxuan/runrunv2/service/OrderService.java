package com.zhangxuan.runrunv2.service;

import com.zhangxuan.runrunv2.dto.CreateOrderRequest;
import com.zhangxuan.runrunv2.vo.CreatedOrderVO;
import com.zhangxuan.runrunv2.vo.OrderVO;





























/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface OrderService {

    CreatedOrderVO create(String token, String key, CreateOrderRequest request);

    OrderVO get(String token, String id);
}
