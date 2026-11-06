package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.FeedbackHandlingStatus;
import com.zhangxuan.runruncampus.enums.FeedbackType;

import java.time.Instant;

/** 用户反馈响应；保持现有 JSON 字段及序列化契约。 */
public record FeedbackResponse(
        Long id,
        FeedbackType type,
        String content,
        Instant createdAt,
        FeedbackHandlingStatus handlingStatus,
        String resolution,
        Instant handledAt,
        Instant updatedAt) {}
