package com.zhangxuan.runruncampus.vo;

import java.util.List;

/** 管理员操作审计分页响应；保持现有 JSON 字段及序列化契约。 */
public record AdminAuditPageResponse(
        List<AdminAuditItem> items, int page, int size, long totalElements, int totalPages) {}
