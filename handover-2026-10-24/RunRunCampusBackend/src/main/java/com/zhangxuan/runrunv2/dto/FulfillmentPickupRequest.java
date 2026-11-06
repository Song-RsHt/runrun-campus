package com.zhangxuan.runrunv2.dto;

public record FulfillmentPickupRequest(Long expectedVersion, String taskId, String pickupProofId) {
}
