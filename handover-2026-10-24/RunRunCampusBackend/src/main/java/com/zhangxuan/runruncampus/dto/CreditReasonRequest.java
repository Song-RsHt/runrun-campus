package com.zhangxuan.runruncampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;





public record CreditReasonRequest(@NotBlank @Size(min = 5, max = 300) String reason) {
}
