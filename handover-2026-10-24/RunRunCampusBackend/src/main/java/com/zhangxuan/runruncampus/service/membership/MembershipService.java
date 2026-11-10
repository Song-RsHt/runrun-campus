package com.zhangxuan.runruncampus.service.membership;

import com.zhangxuan.runruncampus.entity.user.AppUser;
import com.zhangxuan.runruncampus.enums.membership.MembershipPlanType;
import com.zhangxuan.runruncampus.model.membership.MembershipApplication;
import com.zhangxuan.runruncampus.vo.membership.ActiveMembershipResponse;
import com.zhangxuan.runruncampus.vo.membership.MembershipOverview;

/** 用户会员套餐业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface MembershipService {

    /**
     * 查询用户会员套餐概览。
     *
     * @param customer 当前用户账号
     * @return 用户会员套餐概览
     */
    MembershipOverview overview(AppUser customer);

    /**
     * 为用户购买会员套餐；保持原购买资格、价格及权益激活条件。
     *
     * @param customer 当前用户账号
     * @param plan 会员套餐类型
     * @return 有效会员
     */
    ActiveMembershipResponse purchase(AppUser customer, MembershipPlanType plan);

    /**
     * 检查用户是否满足会员套餐购买条件。
     *
     * @param customer 当前用户账号
     */
    void validateCanPurchase(AppUser customer);

    /**
     * 支付确认后激活既有套餐权益。
     *
     * @param customer 当前用户账号
     * @param plan 会员套餐类型
     * @return 有效会员
     */
    ActiveMembershipResponse activatePaidPurchase(AppUser customer, MembershipPlanType plan);

    /**
     * 下单时校验用户有效月卡并扣减一次可用次数。
     *
     * @param customer 当前用户账号
     * @param useMembership 是否使用会员权益；为 false 时不扣减次数
     * @return 本次是否使用会员权益、套餐名称及权益记录标识
     */
    MembershipApplication apply(AppUser customer, boolean useMembership);

    /**
     * 恢复取消订单已占用的用户会员套餐权益。
     *
     * @param customer 当前用户账号
     * @param membershipId 会员权益记录标识
     */
    void refund(AppUser customer, Long membershipId);
}
