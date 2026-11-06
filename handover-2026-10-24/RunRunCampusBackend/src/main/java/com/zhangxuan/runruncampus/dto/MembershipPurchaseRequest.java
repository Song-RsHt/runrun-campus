package com.zhangxuan.runruncampus.dto;

import com.zhangxuan.runruncampus.enums.MembershipPlanType;
import jakarta.validation.constraints.NotNull;

public record MembershipPurchaseRequest(@NotNull(message = "请选择月卡套餐") MembershipPlanType plan) {
}
