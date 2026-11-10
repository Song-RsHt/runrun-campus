package com.zhangxuan.runruncampus.vo.admin;

import java.util.List;

/** 管理员异常工单分页响应；保持现有 JSON 字段及序列化契约。 */
public record AdminIssuePageResponse(
        List<AdminIssueItem> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        long overdueTotal) {}
