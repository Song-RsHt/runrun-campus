package com.zhangxuan.runrunv2.dto.request.payment;

import com.zhangxuan.runrunv2.enums.payment.Result;

public record ResultRequest(Long expectedVersion, Result result, String eventId) {}
