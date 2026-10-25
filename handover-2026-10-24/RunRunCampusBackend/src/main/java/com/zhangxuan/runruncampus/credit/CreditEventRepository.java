package com.zhangxuan.runruncampus.credit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CreditEventRepository extends JpaRepository<CreditEvent, Long> {
    Page<CreditEvent> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Optional<CreditEvent> findByIdAndUserId(Long id, Long userId);
    boolean existsByOriginalEventId(Long originalEventId);
}
