package com.zhangxuan.runruncampus.dto.response.admin.order;

import com.zhangxuan.runruncampus.enums.user.UserRole;

public record UserSummary(Long id, String displayName, String phone, UserRole role) {}
