package com.zhangxuan.runruncampus.model;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.UserCoupon;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.repository.UserCouponRepository;
import com.zhangxuan.runruncampus.vo.CouponResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

public record CouponApplication(int discountCents, String couponTitle, Long couponId) {

    public CouponApplication(int discountCents, String couponTitle) {
        this(discountCents, couponTitle, null);
    }
}
