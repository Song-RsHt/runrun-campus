package com.zhangxuan.runruncampus.repository;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.UserMembership;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/** 用户会员 JPA 仓储；只定义持久化查询和写入，不承担权限及业务决策。 */
public interface UserMembershipRepository extends JpaRepository<UserMembership, Long> {

    Optional<UserMembership> findFirstByUserOrderByPurchasedAtDesc(AppUser user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select m from UserMembership m where m.user = :user and m.expiresAt > :now and"
                    + " m.remainingTrips > 0")
    Optional<UserMembership> findActiveLocked(
            @Param("user") AppUser user, @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from UserMembership m where m.id = :id")
    Optional<UserMembership> findLockedById(@Param("id") Long id);
}
