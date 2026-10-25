package com.zhangxuan.runruncampus.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderProofRepository extends JpaRepository<OrderProof, Long> {
    boolean existsByOrderAndType(CampusOrder order, OrderProofType type);
    Optional<OrderProof> findByOrderAndType(CampusOrder order, OrderProofType type);
    List<OrderProof> findByOrderOrderByCreatedAtAsc(CampusOrder order);
}
