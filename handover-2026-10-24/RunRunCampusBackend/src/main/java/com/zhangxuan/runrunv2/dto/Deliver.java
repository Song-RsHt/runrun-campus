package com.zhangxuan.runrunv2.dto;



public record Deliver(Long expectedVersion, String taskId, String deliveryProofId, String locationEventId) {
}
