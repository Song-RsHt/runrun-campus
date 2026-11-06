package com.zhangxuan.runruncampus.vo.admin;

import java.util.List;

/** 管理员可见的订单分页响应；保持现有 JSON 字段及序列化契约。 */
public record AdminOrderPageResponse(
        List<AdminOrderListItem> items, int page, int size, long totalElements, int totalPages) {}
