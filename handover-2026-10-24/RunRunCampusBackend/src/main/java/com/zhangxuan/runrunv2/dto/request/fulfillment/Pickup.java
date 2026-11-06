package com.zhangxuan.runrunv2.dto.request.fulfillment;

public record Pickup(Long expectedVersion, String taskId, String pickupProofId) {}
