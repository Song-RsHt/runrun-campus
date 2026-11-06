package com.zhangxuan.runruncampus.vo;

import java.util.List;

/** 本人订单分页响应；保持现有 JSON 字段及序列化契约。 */
public record MineOrderPage(
        List<OrderResponse> items, int page, int size, long totalElements, int totalPages) {}
