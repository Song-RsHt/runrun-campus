package com.zhangxuan.runruncampus.repository;

import com.zhangxuan.runruncampus.entity.CampusOrder;
import com.zhangxuan.runruncampus.entity.OrderProof;
import com.zhangxuan.runruncampus.enums.OrderProofType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;









public interface OrderProofRepository extends JpaRepository<OrderProof, Long> {

    boolean existsByOrderAndType(CampusOrder order, OrderProofType type);

    Optional<OrderProof> findByOrderAndType(CampusOrder order, OrderProofType type);

    List<OrderProof> findByOrderOrderByCreatedAtAsc(CampusOrder order);
}
