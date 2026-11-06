package com.zhangxuan.runruncampus.vo;

import java.util.List;




public record AdminOrderPageResponse(List<AdminOrderListItem> items, int page, int size, long totalElements, int totalPages) {
}
