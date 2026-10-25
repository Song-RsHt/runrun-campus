package com.zhangxuan.runruncampus.credit;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RiskSignalRepository extends JpaRepository<RiskSignal, Long> {
    long countBySubjectIdAndStatus(Long subjectId, RiskSignalStatus status);
    Page<RiskSignal> findByStatusOrderByCreatedAtAsc(RiskSignalStatus status, Pageable pageable);
    Page<RiskSignal> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select signal from RiskSignal signal join fetch signal.subject where signal.id = :id")
    Optional<RiskSignal> findLockedById(@Param("id") Long id);
}
