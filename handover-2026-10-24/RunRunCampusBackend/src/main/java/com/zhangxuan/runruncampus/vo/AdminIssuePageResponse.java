package com.zhangxuan.runruncampus.vo;

import java.util.List;




public record AdminIssuePageResponse(List<AdminIssueItem> items, int page, int size, long totalElements, int totalPages, long overdueTotal) {
}
