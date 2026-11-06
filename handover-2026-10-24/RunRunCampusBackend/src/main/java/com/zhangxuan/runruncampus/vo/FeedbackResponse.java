package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.FeedbackHandlingStatus;
import com.zhangxuan.runruncampus.enums.FeedbackType;
import java.time.Instant;














public record FeedbackResponse(Long id, FeedbackType type, String content, Instant createdAt, FeedbackHandlingStatus handlingStatus, String resolution, Instant handledAt, Instant updatedAt) {
}
