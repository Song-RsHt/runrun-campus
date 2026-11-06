package com.zhangxuan.runruncampus.vo;

import java.util.List;

/** 用户反馈分页响应；保持现有 JSON 字段及序列化契约。 */
public record FeedbackPageResponse(
        List<FeedbackItem> items, int page, int size, long totalElements, int totalPages) {}
