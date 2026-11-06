package com.zhangxuan.runruncampus.repository;

import com.zhangxuan.runruncampus.entity.CreditProfile;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;










public interface CreditProfileRepository extends JpaRepository<CreditProfile, Long> {

    Optional<CreditProfile> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select profile from CreditProfile profile where profile.userId = :userId")
    Optional<CreditProfile> findLockedByUserId(@Param("userId") Long userId);
}
