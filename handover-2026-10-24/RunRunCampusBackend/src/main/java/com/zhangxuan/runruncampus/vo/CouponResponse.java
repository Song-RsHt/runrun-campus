package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.UserCoupon;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.model.CouponApplication;
import com.zhangxuan.runruncampus.repository.UserCouponRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

public record CouponResponse(Long id, String title, int discountCents, int minimumOrderCents, boolean used, boolean expired, Instant expiresAt, Instant usedAt) {
}
