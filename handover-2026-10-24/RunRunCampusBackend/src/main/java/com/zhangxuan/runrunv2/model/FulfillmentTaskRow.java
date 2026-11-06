package com.zhangxuan.runrunv2.model;

import java.time.OffsetDateTime;

/**
 * Persistence projection; allowed HTTP actions are derived by the query service.
 */
public record FulfillmentTaskRow(String taskId, String taskStatus, long taskVersion, String orderId, String orderNo, String orderStatus, long orderVersion, OffsetDateTime createdAt, boolean superseded) {
}
