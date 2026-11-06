package com.zhangxuan.runruncampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;





public record AdminUserPasswordResetRequest(@NotBlank(message = "请输入新密码") @Size(min = 6, max = 72, message = "密码长度应为 6 到 72 位") String password) {
}
