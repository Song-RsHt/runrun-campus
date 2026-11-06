package com.zhangxuan.runrunv2.model;

import com.zhangxuan.runrunv2.enums.Kind;






public record EvidenceBinding(String orderId, String taskId, String actorId, Kind kind) {
}
