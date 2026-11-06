package com.zhangxuan.runruncampus.repository;

import com.zhangxuan.runruncampus.entity.CampusOrder;
import com.zhangxuan.runruncampus.entity.OrderEvent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 订单创建与状态管理事件 JPA 仓储；只定义持久化查询和写入，不承担权限及业务决策。 */
public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {

    List<OrderEvent> findByOrderOrderByCreatedAtAsc(CampusOrder order);
}
