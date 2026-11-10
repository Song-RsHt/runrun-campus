package com.zhangxuan.runruncampus.repository.credit;

import com.zhangxuan.runruncampus.entity.credit.CreditEvent;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 信用分及风险信号事件 JPA 仓储；只定义持久化查询和写入，不承担权限及业务决策。 */
public interface CreditEventRepository extends JpaRepository<CreditEvent, Long> {

    Page<CreditEvent> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<CreditEvent> findByIdAndUserId(Long id, Long userId);

    boolean existsByOriginalEventId(Long originalEventId);
}
