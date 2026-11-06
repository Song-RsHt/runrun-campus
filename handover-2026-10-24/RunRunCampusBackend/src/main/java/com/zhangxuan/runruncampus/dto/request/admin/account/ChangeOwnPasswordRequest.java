package com.zhangxuan.runruncampus.dto.request.admin.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeOwnPasswordRequest(
    @NotBlank(message = "请输入当前密码") String currentPassword,
    @Size(min = 8, max = 72, message = "新密码长度应为 8 到 72 位") String newPassword) {}
