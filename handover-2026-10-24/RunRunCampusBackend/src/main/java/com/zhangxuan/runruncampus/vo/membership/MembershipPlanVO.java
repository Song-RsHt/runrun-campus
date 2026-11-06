package com.zhangxuan.runruncampus.vo.membership;

import com.zhangxuan.runruncampus.enums.membership.MembershipPlanType;

/** 用户会员套餐套餐响应；保持现有 JSON 字段及序列化契约。 */
public record MembershipPlanVO(
        MembershipPlanType type, String title, int trips, int priceCents, String note) {}
