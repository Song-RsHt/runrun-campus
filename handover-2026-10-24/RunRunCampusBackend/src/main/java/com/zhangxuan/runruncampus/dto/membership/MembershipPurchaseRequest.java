package com.zhangxuan.runruncampus.dto.membership;

import com.zhangxuan.runruncampus.enums.membership.MembershipPlanType;

import jakarta.validation.constraints.NotNull;

/** 用户会员套餐购买请求参数；保留入口的字段、构造器及校验契约。 */
public record MembershipPurchaseRequest(@NotNull(message = "请选择月卡套餐") MembershipPlanType plan) {}
