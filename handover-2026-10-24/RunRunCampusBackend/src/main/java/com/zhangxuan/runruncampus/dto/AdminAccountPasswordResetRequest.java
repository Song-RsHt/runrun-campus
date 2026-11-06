package com.zhangxuan.runruncampus.dto;

import jakarta.validation.constraints.Size;

public record AdminAccountPasswordResetRequest(@Size(min = 8, max = 72, message = "管理员密码长度应为 8 到 72 位") String password) {
}
