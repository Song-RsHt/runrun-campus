package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.UserRole;




public record UserSummary(Long id, String displayName, String phone, UserRole role) {
}
