package com.zhangxuan.runrunv2.model.fulfillment;

import java.time.OffsetDateTime;

/** 履约任务持久化投影；允许的 HTTP 操作由查询服务按权限推导。 */
public record FulfillmentTaskRow(
        String taskId,
        String taskStatus,
        long taskVersion,
        String orderId,
        String orderNo,
        String orderStatus,
        long orderVersion,
        OffsetDateTime createdAt,
        boolean superseded) {}
