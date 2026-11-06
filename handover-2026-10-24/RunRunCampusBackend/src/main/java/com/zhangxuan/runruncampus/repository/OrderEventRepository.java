package com.zhangxuan.runruncampus.repository;

import com.zhangxuan.runruncampus.entity.CampusOrder;
import com.zhangxuan.runruncampus.entity.OrderEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;







public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {

    List<OrderEvent> findByOrderOrderByCreatedAtAsc(CampusOrder order);
}
