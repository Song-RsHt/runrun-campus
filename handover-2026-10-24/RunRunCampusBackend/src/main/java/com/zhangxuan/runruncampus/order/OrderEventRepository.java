package com.zhangxuan.runruncampus.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {
    List<OrderEvent> findByOrderOrderByCreatedAtAsc(CampusOrder order);
}
