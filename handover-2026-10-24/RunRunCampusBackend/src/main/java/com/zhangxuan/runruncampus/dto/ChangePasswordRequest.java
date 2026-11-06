package com.zhangxuan.runruncampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;





public record ChangePasswordRequest(@NotBlank(message = "请输入当前密码") @Size(max = 72, message = "当前密码过长") String currentPassword, @NotBlank(message = "请输入新密码") @Size(min = 6, max = 72, message = "密码长度应为 6 到 72 位") String newPassword) {
}
