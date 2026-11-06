package com.zhangxuan.runrunv2.vo;

import com.zhangxuan.runrunv2.enums.Kind;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderProofVO(String id, String orderId, String taskId, Kind kind, String storageStatus, long orderVersion) {
}
