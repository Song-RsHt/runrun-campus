package com.zhangxuan.runruncampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreditSignalDecisionRequest(@NotBlank String decision, long expectedVersion, String photoResourceId, @Size(max = 300) String reason) {
}
