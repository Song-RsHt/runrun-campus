package com.zhangxuan.runrunv2.mapper.order;

import com.zhangxuan.runrunv2.vo.order.OrderVO;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 订单快照读取映射；锁模式仅由 OrderStore 的受控参数选择，使用调用方事务。 */
@Mapper
public interface OrderReadMapper {
    List<OrderVO> findOrder(@Param("orderId") long orderId, @Param("locked") boolean locked);
}
