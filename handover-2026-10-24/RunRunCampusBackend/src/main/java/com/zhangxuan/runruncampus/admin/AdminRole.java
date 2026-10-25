package com.zhangxuan.runruncampus.admin;

import java.util.EnumSet;
import java.util.Set;

public enum AdminRole {
    SUPER_ADMIN(EnumSet.allOf(AdminPermission.class)),
    OPERATIONS(EnumSet.of(
            AdminPermission.DASHBOARD_VIEW, AdminPermission.ORDER_VIEW, AdminPermission.ORDER_DISPATCH,
            AdminPermission.ORDER_ARCHIVE,
            AdminPermission.PROVENANCE_VIEW, AdminPermission.PROVENANCE_RECORD,
            AdminPermission.PAYMENT_REVIEW,
            AdminPermission.PLATFORM_SETTINGS_MANAGE,
            AdminPermission.PUSH_DELIVERY_VIEW,
            AdminPermission.ORDER_EXPORT,
            AdminPermission.USER_VIEW, AdminPermission.COUPON_ISSUE,
            AdminPermission.COURIER_VIEW, AdminPermission.COURIER_REVIEW,
            AdminPermission.ISSUE_VIEW, AdminPermission.ISSUE_MANAGE,
            AdminPermission.FEEDBACK_VIEW, AdminPermission.FEEDBACK_MANAGE)),
    SUPPORT(EnumSet.of(
            AdminPermission.DASHBOARD_VIEW, AdminPermission.ORDER_VIEW, AdminPermission.USER_VIEW,
            AdminPermission.ISSUE_VIEW, AdminPermission.ISSUE_MANAGE,
            AdminPermission.FEEDBACK_VIEW, AdminPermission.FEEDBACK_MANAGE)),
    REVIEWER(EnumSet.of(
            AdminPermission.DASHBOARD_VIEW, AdminPermission.USER_VIEW,
            AdminPermission.COURIER_VIEW, AdminPermission.COURIER_REVIEW)),
    AUDITOR(EnumSet.of(
            AdminPermission.DASHBOARD_VIEW, AdminPermission.ORDER_VIEW, AdminPermission.USER_VIEW,
            AdminPermission.COURIER_VIEW, AdminPermission.ISSUE_VIEW, AdminPermission.PROVENANCE_VIEW,
            AdminPermission.PROVENANCE_VERIFY,
            AdminPermission.FEEDBACK_VIEW, AdminPermission.AUDIT_VIEW));

    private final Set<AdminPermission> permissions;

    AdminRole(Set<AdminPermission> permissions) {
        this.permissions = Set.copyOf(permissions);
    }

    public Set<AdminPermission> getPermissions() {
        return permissions;
    }

    public boolean permits(AdminPermission permission) {
        return permissions.contains(permission);
    }
}
