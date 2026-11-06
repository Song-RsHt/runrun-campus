package com.zhangxuan.runruncampus.vo;

import java.util.List;




public record AdminAuditPageResponse(List<AdminAuditItem> items, int page, int size, long totalElements, int totalPages) {
}
