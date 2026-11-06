package com.zhangxuan.runruncampus.model;

/** 用户会员套餐启动；仅承载内部协作所需的数据或校验逻辑。 */
public record MembershipApplication(boolean used, String planTitle, Long membershipId) {

    public MembershipApplication(boolean used, String planTitle) {
        this(used, planTitle, null);
    }
}
