package com.zhangxuan.runrunv2.dto;

import com.zhangxuan.runrunv2.enums.Decision;

public record FulfillmentRespondRequest(Long expectedVersion, Decision decision) {
}
