package com.zhangxuan.runruncampus.dto.request.membership;

import com.zhangxuan.runruncampus.enums.membership.MembershipPlanType;
import jakarta.validation.constraints.NotNull;

public record PurchaseRequest(@NotNull(message = "请选择月卡套餐") MembershipPlanType plan) {}
