package com.zhangxuan.runruncampus.v2.foundation;

public final class ResourceChecks {
    private ResourceChecks() {}
    /** Authorize before inspecting version/flow to avoid disclosing another person's resource. */
    public static void visible(boolean authorized) {
        if (!authorized) throw new V2Exception(V2Error.RESOURCE_NOT_FOUND, "资源不存在");
    }
    public static void version(long actual, Long expected) {
        if (expected == null || expected < 0) throw new V2Exception(V2Error.VALIDATION_ERROR, "expectedVersion必填且非负");
        if (actual != expected) throw new V2Exception(V2Error.ORDER_STATE_CONFLICT, "资源已更新，请刷新");
    }
    public static void order(boolean authorized, int flowVersion, long actual, Long expected) {
        visible(authorized);
        if (flowVersion != 2) throw new V2Exception(V2Error.ORDER_FLOW_VERSION_MISMATCH, "此入口不支持历史订单");
        version(actual, expected);
    }
}
