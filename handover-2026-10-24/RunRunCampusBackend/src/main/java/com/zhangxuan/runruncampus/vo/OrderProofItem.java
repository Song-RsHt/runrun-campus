package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.OrderProofType;
import java.time.Instant;

public record OrderProofItem(Long id, OrderProofType type, String originalFileName, String contentType, long sizeBytes, UserSummary uploader, Instant createdAt) {
}
