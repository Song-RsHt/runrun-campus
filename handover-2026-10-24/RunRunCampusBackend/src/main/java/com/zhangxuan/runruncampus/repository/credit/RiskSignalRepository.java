package com.zhangxuan.runruncampus.repository.credit;

import com.zhangxuan.runruncampus.enums.credit.RiskSignalStatus;
import com.zhangxuan.runruncampus.model.credit.RiskSignal;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RiskSignalRepository extends JpaRepository<RiskSignal, Long> {

  long countBySubjectIdAndStatus(Long subjectId, RiskSignalStatus status);

  Page<RiskSignal> findByStatusOrderByCreatedAtAsc(RiskSignalStatus status, Pageable pageable);

  Page<RiskSignal> findAllByOrderByCreatedAtDesc(Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select signal from RiskSignal signal join fetch signal.subject where signal.id = :id")
  Optional<RiskSignal> findLockedById(@Param("id") Long id);
}
