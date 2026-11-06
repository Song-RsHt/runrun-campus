package com.zhangxuan.runruncampus.vo;

import java.util.List;

public record MineOrderPage(List<OrderResponse> items, int page, int size, long totalElements, int totalPages) {
}
