package com.zhangxuan.runruncampus.vo;

import java.util.List;

/** 管理员订单查询与导出分页响应；保持现有 JSON 字段及序列化契约。 */
public record AdminOrderPageResponse(
        List<AdminOrderListItem> items, int page, int size, long totalElements, int totalPages) {}
