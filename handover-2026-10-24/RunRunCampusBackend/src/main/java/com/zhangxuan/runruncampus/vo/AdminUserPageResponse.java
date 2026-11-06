package com.zhangxuan.runruncampus.vo;

import java.util.List;




public record AdminUserPageResponse(List<AdminUserItem> items, int page, int size, long totalElements, int totalPages) {
}
