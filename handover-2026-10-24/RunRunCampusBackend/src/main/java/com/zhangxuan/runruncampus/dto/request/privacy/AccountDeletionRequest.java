package com.zhangxuan.runruncampus.dto.request.privacy;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountDeletionRequest(
    @NotBlank(message = "请输入当前密码") @Size(max = 72) String password,
    @AssertTrue(message = "请确认注销账号") boolean confirmed) {}
