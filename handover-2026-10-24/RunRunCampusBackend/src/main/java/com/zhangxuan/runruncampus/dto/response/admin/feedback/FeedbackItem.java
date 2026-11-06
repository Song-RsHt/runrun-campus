package com.zhangxuan.runruncampus.dto.response.admin.feedback;

import com.zhangxuan.runruncampus.enums.feedback.FeedbackHandlingStatus;
import com.zhangxuan.runruncampus.enums.feedback.FeedbackType;
import java.time.Instant;

public record FeedbackItem(
    Long id,
    FeedbackType type,
    String content,
    Instant createdAt,
    Long userId,
    String userName,
    String userPhone,
    FeedbackHandlingStatus handlingStatus,
    Long assigneeId,
    String assigneeName,
    String resolution,
    Instant handledAt,
    Instant updatedAt) {}
