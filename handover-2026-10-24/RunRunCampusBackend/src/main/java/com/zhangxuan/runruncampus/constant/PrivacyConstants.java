package com.zhangxuan.runruncampus.constant;

import com.zhangxuan.runruncampus.enums.OrderStatus;

import java.util.List;

/** 隐私同意及账号注销常量；集中保存既有业务常量。 */
public final class PrivacyConstants {

    private PrivacyConstants() {}

    public static final String CURRENT_VERSION = "2026-10-24";

    public static final List<OrderStatus> ACTIVE_ORDER_STATUSES =
            List.of(
                    OrderStatus.PENDING_ASSIGNMENT,
                    OrderStatus.PENDING_REASSIGNMENT,
                    OrderStatus.ACCEPTED,
                    OrderStatus.PICKED_UP);
}
