package com.zhangxuan.runruncampus.model;

import java.util.List;












public record PushDeliveryPage(List<PushDeliveryItem> items, int page, int size, long totalElements, int totalPages) {
}
