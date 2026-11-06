package com.zhangxuan.runrunv2.dto.request.fulfillment;

import com.zhangxuan.runrunv2.enums.fulfillment.Kind;

public record RegisterProof(Long expectedVersion, Kind kind, String taskId, String uploadTicket) {}
