package com.zhangxuan.runruncampus.repository.order;

import com.zhangxuan.runruncampus.enums.order.OrderProofType;
import com.zhangxuan.runruncampus.model.order.CampusOrder;
import com.zhangxuan.runruncampus.model.order.OrderProof;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderProofRepository extends JpaRepository<OrderProof, Long> {
  boolean existsByOrderAndType(CampusOrder order, OrderProofType type);

  Optional<OrderProof> findByOrderAndType(CampusOrder order, OrderProofType type);

  List<OrderProof> findByOrderOrderByCreatedAtAsc(CampusOrder order);
}
