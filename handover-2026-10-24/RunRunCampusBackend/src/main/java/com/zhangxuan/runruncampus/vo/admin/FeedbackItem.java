package com.zhangxuan.runruncampus.vo.admin;

import com.zhangxuan.runruncampus.enums.feedback.FeedbackHandlingStatus;
import com.zhangxuan.runruncampus.enums.feedback.FeedbackType;

import java.time.Instant;

/** 用户反馈条目响应；保持现有 JSON 字段及序列化契约。 */
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
