package com.zhangxuan.runruncampus.repository;

import com.zhangxuan.runruncampus.entity.CreditProfile;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** 信用分及风险信号资料 JPA 仓储；只定义持久化查询和写入，不承担权限及业务决策。 */
public interface CreditProfileRepository extends JpaRepository<CreditProfile, Long> {

    Optional<CreditProfile> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select profile from CreditProfile profile where profile.userId = :userId")
    Optional<CreditProfile> findLockedByUserId(@Param("userId") Long userId);
}
