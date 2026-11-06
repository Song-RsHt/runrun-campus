package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.model.CouponApplication;
import com.zhangxuan.runruncampus.vo.CouponResponse;
import java.util.List;
















/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface CouponService {

    List<CouponResponse> list(AppUser customer);

    CouponApplication apply(AppUser customer, Long couponId, int originalFeeCents);

    void refund(AppUser customer, Long couponId);
}
