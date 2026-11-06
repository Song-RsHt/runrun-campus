package com.zhangxuan.runruncampus.dto.request.auth.mfa;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetRequest(@NotBlank @Size(max = 200) String reason) {}
