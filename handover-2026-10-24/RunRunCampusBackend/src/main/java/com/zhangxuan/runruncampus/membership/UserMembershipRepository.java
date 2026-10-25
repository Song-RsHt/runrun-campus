package com.zhangxuan.runruncampus.membership;

import com.zhangxuan.runruncampus.user.AppUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface UserMembershipRepository extends JpaRepository<UserMembership, Long> {
    Optional<UserMembership> findFirstByUserOrderByPurchasedAtDesc(AppUser user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from UserMembership m where m.user = :user and m.expiresAt > :now and m.remainingTrips > 0")
    Optional<UserMembership> findActiveLocked(@Param("user") AppUser user, @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from UserMembership m where m.id = :id")
    Optional<UserMembership> findLockedById(@Param("id") Long id);
}
