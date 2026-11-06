package com.zhangxuan.runruncampus.dto.request.credit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReasonRequest(@NotBlank @Size(min = 5, max = 300) String reason) {}
