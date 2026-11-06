package com.zhangxuan.runruncampus.vo;

import java.util.List;

public record FeedbackPageResponse(List<FeedbackItem> items, int page, int size, long totalElements, int totalPages) {
}
