package com.zhangxuan.runruncampus.repository.order;

import com.zhangxuan.runruncampus.entity.order.CampusOrder;
import com.zhangxuan.runruncampus.entity.order.OrderProof;
import com.zhangxuan.runruncampus.enums.order.OrderProofType;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 订单私有图片凭证 JPA 仓储；只定义持久化查询和写入，不承担权限及业务决策。 */
public interface OrderProofRepository extends JpaRepository<OrderProof, Long> {

    boolean existsByOrderAndType(CampusOrder order, OrderProofType type);

    Optional<OrderProof> findByOrderAndType(CampusOrder order, OrderProofType type);

    List<OrderProof> findByOrderOrderByCreatedAtAsc(CampusOrder order);
}
