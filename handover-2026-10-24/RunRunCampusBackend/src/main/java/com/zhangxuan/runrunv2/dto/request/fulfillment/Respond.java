package com.zhangxuan.runrunv2.dto.request.fulfillment;

import com.zhangxuan.runrunv2.enums.fulfillment.Decision;

public record Respond(Long expectedVersion, Decision decision) {}
