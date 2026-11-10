package com.zhangxuan.runruncampus.service.admin;

import com.zhangxuan.runruncampus.entity.user.AppUser;
import com.zhangxuan.runruncampus.vo.admin.CouponGrantResponse;

/** 管理员优惠券发放业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface AdminCouponService {

    /**
     * 向符合条件的用户发放既有一元优惠券，并记录审计。
     *
     * @param administrator 已通过权限校验的管理员
     * @param userId 目标用户标识
     * @return 发放后的优惠券记录
     */
    CouponGrantResponse issueOneYuan(AppUser administrator, Long userId);
}
