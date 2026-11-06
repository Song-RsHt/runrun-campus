package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.FeedbackHandlingStatus;
import com.zhangxuan.runruncampus.enums.FeedbackType;
import java.time.Instant;

public record FeedbackItem(Long id, FeedbackType type, String content, Instant createdAt, Long userId, String userName, String userPhone, FeedbackHandlingStatus handlingStatus, Long assigneeId, String assigneeName, String resolution, Instant handledAt, Instant updatedAt) {
}
