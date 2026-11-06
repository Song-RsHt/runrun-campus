package com.zhangxuan.runruncampus.repository;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.UserCoupon;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** 用户优惠券 JPA 仓储；只定义持久化查询和写入，不承担权限及业务决策。 */
public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {

    boolean existsByUser(AppUser user);

    List<UserCoupon> findByUserOrderByCreatedAtDesc(AppUser user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from UserCoupon c where c.id = :id")
    Optional<UserCoupon> findLockedById(@Param("id") Long id);
}
