package com.zhangxuan.runruncampus.vo;

import java.util.List;

public record AdminCourierOrderPage(List<AdminCourierOrderItem> items, int page, int size, long totalElements, int totalPages) {
}
