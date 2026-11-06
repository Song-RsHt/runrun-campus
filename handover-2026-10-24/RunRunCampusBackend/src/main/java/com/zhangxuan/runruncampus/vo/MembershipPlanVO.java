package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.MembershipPlanType;


















public record MembershipPlanVO(MembershipPlanType type, String title, int trips, int priceCents, String note) {
}
