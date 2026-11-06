package com.zhangxuan.runrunv2.dto;

public record FulfillmentDeliverRequest(Long expectedVersion, String taskId, String deliveryProofId, String locationEventId) {
}
