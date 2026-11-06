package com.zhangxuan.runruncampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;





public record OrderLifecycleReasonRequest(@NotBlank @Size(min = 5, max = 200) String reason) {
}
