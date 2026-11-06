package com.zhangxuan.runruncampus.dto.response.admin.user;

import java.util.List;

public record AdminUserPageResponse(
    List<AdminUserItem> items, int page, int size, long totalElements, int totalPages) {}
