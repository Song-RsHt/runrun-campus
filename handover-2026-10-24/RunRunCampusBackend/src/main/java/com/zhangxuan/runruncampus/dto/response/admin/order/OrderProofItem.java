package com.zhangxuan.runruncampus.dto.response.admin.order;

import com.zhangxuan.runruncampus.enums.order.OrderProofType;
import java.time.Instant;

public record OrderProofItem(
    Long id,
    OrderProofType type,
    String originalFileName,
    String contentType,
    long sizeBytes,
    UserSummary uploader,
    Instant createdAt) {}
