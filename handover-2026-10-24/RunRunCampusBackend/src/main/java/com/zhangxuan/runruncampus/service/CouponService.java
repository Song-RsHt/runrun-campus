package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.model.CouponApplication;
import com.zhangxuan.runruncampus.vo.CouponResponse;

import java.util.List;

/** 用户优惠券业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface CouponService {

    /**
     * 查询用户优惠券列表，保留现有筛选和分页口径。
     *
     * @param customer 当前用户账号
     * @return 用户优惠券列表，顺序和筛选沿用原查询
     */
    List<CouponResponse> list(AppUser customer);

    /**
     * 下单时校验优惠券归属、有效期及使用状态，并按订单费用计算抵扣。
     *
     * @param customer 当前用户账号
     * @param couponId 用户优惠券标识
     * @param originalFeeCents 使用优惠前的原始费用，单位分
     * @return 实际抵扣金额及所用优惠券标识
     */
    CouponApplication apply(AppUser customer, Long couponId, int originalFeeCents);

    /**
     * 恢复取消订单已占用的用户优惠券权益。
     *
     * @param customer 当前用户账号
     * @param couponId 用户优惠券标识
     */
    void refund(AppUser customer, Long couponId);
}
