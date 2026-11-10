package com.zhangxuan.runruncampus.vo.admin;

import java.util.List;

/** 用户分页及账号状态响应；保持现有 JSON 字段及序列化契约。 */
public record AdminUserPageResponse(
        List<AdminUserItem> items, int page, int size, long totalElements, int totalPages) {}
