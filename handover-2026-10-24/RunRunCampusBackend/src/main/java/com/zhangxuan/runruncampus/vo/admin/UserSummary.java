package com.zhangxuan.runruncampus.vo.admin;

import com.zhangxuan.runruncampus.enums.user.UserRole;

/** 用户汇总响应；保持现有 JSON 字段及序列化契约。 */
public record UserSummary(Long id, String displayName, String phone, UserRole role) {}
