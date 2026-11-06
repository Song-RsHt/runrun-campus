package com.zhangxuan.runrunv2.dto;

public record FulfillmentReleaseRequest(Long expectedVersion, String taskId, String reason) {
}
