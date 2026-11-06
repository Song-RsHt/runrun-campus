package com.zhangxuan.runruncampus.dto.response.admin.feedback;

import java.util.List;

public record FeedbackPageResponse(
    List<FeedbackItem> items, int page, int size, long totalElements, int totalPages) {}
