package com.zhangxuan.runruncampus.dto.request.auth.mfa;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReplaceRequest(
    @NotBlank @Size(max = 72) String password, @NotBlank @Size(max = 128) String code) {}
