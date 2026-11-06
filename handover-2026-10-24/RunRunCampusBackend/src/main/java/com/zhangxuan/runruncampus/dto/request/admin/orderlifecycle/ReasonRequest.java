package com.zhangxuan.runruncampus.dto.request.admin.orderlifecycle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReasonRequest(@NotBlank @Size(min = 5, max = 200) String reason) {}
