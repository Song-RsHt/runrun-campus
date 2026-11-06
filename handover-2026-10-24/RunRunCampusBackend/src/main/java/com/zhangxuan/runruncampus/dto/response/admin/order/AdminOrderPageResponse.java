package com.zhangxuan.runruncampus.dto.response.admin.order;

import java.util.List;

public record AdminOrderPageResponse(
    List<AdminOrderListItem> items, int page, int size, long totalElements, int totalPages) {}
