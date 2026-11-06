package com.zhangxuan.runruncampus.vo;

import java.util.List;

/** 用户会员套餐概览响应；保持现有 JSON 字段及序列化契约。 */
public record MembershipOverview(ActiveMembershipResponse active, List<MembershipPlanVO> plans) {}
