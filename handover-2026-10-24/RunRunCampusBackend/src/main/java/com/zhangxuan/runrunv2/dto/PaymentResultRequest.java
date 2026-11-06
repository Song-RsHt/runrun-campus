package com.zhangxuan.runrunv2.dto;

import com.zhangxuan.runrunv2.enums.Result;

public record PaymentResultRequest(Long expectedVersion, Result result, String eventId) {
}
