package com.zhangxuan.runrunv2.service.order;

import com.zhangxuan.runrunv2.dto.order.CreateOrderRequest;
import com.zhangxuan.runrunv2.vo.order.CreatedOrderVO;
import com.zhangxuan.runrunv2.vo.order.OrderVO;

/** 订单创建与状态管理业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface OrderService {

    /**
     * 校验账号、地址、报价及幂等键后创建订单，保持原支付义务与通知原子提交。
     *
     * <p>按原幂等作用域重放成功结果；后续配置或营业状态改变不能改变原结果或延长截止时间。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param key 本次操作的幂等键，作用域由现有业务规则决定
     * @param request 下单地址、服务规格及计价校验参数
     * @return 已创建订单
     */
    CreatedOrderVO create(String token, String key, CreateOrderRequest request);

    /**
     * 读取当前账号有权访问的订单详情。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param id 目标业务记录标识
     * @return 订单详情及当前状态
     */
    OrderVO get(String token, String id);
}
