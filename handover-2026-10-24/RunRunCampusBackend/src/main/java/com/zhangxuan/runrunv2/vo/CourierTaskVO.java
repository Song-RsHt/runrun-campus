package com.zhangxuan.runrunv2.vo;

import java.time.OffsetDateTime;
import java.util.List;






public record CourierTaskVO(String taskId, String taskStatus, long taskVersion, int flowVersion, String orderId, String orderNo, String orderStatus, long orderVersion, OffsetDateTime createdAt, List<String> allowedActions) {
}
