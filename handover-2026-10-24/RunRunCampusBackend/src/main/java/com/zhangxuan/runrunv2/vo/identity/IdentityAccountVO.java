package com.zhangxuan.runrunv2.vo.identity;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** 新系统账号响应；保持现有 JSON 字段及序列化契约。 */
public record IdentityAccountVO(
        String id,
        String role,
        String status,
        boolean adminEnabled,
        long securityVersion,
        boolean courierEligible) {

    // record 组件之外的派生属性须显式序列化，登录与会话恢复共用同一契约。
    @JsonProperty("permissions")
    public List<String> permissions() {
        if (!"ADMIN".equals(role) || !adminEnabled || !"ACTIVE".equals(status)) {
            return List.of();
        }
        return List.of(
                "DASHBOARD_VIEW",
                "ORDER_VIEW",
                "ORDER_DISPATCH",
                "ORDER_FORCE_COMPLETE",
                "ORDER_ARCHIVE",
                "PAYMENT_REVIEW",
                "PLATFORM_SETTINGS_MANAGE",
                "ORDER_EXPORT",
                "USER_VIEW",
                "USER_MANAGE",
                "COURIER_VIEW",
                "COURIER_REVIEW",
                "ISSUE_VIEW",
                "ISSUE_MANAGE",
                "FEEDBACK_VIEW",
                "FEEDBACK_MANAGE",
                "AUDIT_VIEW",
                "ADMIN_MANAGE");
    }
}
