package com.zhangxuan.runrunv2.dto;

import com.zhangxuan.runrunv2.enums.Kind;

public record RegisterProof(Long expectedVersion, Kind kind, String taskId, String uploadTicket) {
}
