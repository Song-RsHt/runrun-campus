package com.zhangxuan.runrunv2.dto.request.fulfillment;

public record Deliver(
    Long expectedVersion, String taskId, String deliveryProofId, String locationEventId) {}
