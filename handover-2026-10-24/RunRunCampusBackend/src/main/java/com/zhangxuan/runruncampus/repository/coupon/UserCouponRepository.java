package com.zhangxuan.runruncampus.repository.coupon;

import com.zhangxuan.runruncampus.model.coupon.UserCoupon;
import com.zhangxuan.runruncampus.model.user.AppUser;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {

  boolean existsByUser(AppUser user);

  List<UserCoupon> findByUserOrderByCreatedAtDesc(AppUser user);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from UserCoupon c where c.id = :id")
  Optional<UserCoupon> findLockedById(@Param("id") Long id);
}
