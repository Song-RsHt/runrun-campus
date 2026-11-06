package com.zhangxuan.runruncampus.dto.request.admin.account;

import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
    @Size(min = 8, max = 72, message = "管理员密码长度应为 8 到 72 位") String password) {}
