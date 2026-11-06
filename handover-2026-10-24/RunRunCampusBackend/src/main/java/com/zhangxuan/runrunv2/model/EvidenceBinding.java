package com.zhangxuan.runrunv2.model;

import com.zhangxuan.runrunv2.enums.Kind;
import com.zhangxuan.runrunv2.vo.UploadTicketVO;
import java.time.OffsetDateTime;

public record EvidenceBinding(String orderId, String taskId, String actorId, Kind kind) {
}
