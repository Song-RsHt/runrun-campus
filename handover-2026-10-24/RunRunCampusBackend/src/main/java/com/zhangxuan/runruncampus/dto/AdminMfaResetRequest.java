package com.zhangxuan.runruncampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminMfaResetRequest(@NotBlank @Size(max = 200) String reason) {
}
