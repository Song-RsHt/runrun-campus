package com.zhangxuan.runruncampus.vo;

import java.util.List;

/** 管理员骑手查询订单分页响应；保持现有 JSON 字段及序列化契约。 */
public record AdminCourierOrderPage(
        List<AdminCourierOrderItem> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
