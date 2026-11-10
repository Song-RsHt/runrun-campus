package com.zhangxuan.runruncampus.model.membership;

/** 本次是否使用会员权益、套餐名称及权益记录标识；仅承载内部协作所需的数据或校验逻辑。 */
public record MembershipApplication(boolean used, String planTitle, Long membershipId) {

    public MembershipApplication(boolean used, String planTitle) {
        this(used, planTitle, null);
    }
}
