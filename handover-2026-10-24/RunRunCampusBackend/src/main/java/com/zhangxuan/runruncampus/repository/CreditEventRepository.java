package com.zhangxuan.runruncampus.repository;

import com.zhangxuan.runruncampus.entity.CreditEvent;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;








public interface CreditEventRepository extends JpaRepository<CreditEvent, Long> {

    Page<CreditEvent> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<CreditEvent> findByIdAndUserId(Long id, Long userId);

    boolean existsByOriginalEventId(Long originalEventId);
}
