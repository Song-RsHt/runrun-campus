package com.zhangxuan.runruncampus.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;







public record AdjustmentRequest(@Min(-100) @Max(100) int delta, @NotBlank @Size(min = 5, max = 300) String reason) {
}
