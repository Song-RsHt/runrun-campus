package com.zhangxuan.runruncampus.vo;

import java.util.List;

/** 骑手分页及账号状态响应；保持现有 JSON 字段及序列化契约。 */
public record AdminCourierPageResponse(
        List<AdminCourierItem> items, int page, int size, long totalElements, int totalPages) {}
