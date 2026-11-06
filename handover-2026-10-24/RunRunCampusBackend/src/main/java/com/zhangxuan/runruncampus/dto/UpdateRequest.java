package com.zhangxuan.runruncampus.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateRequest(@NotBlank @Size(max = 80) String businessHours, @Min(1) @Max(1440) int waitMinutes, @Min(1) @Max(1440) int deliveryMinutes, @Min(1) @Max(10) int maxActiveOrders, @Min(1) @Max(1440) int issuePendingMinutes, @Min(1) @Max(1440) int issueProcessingMinutes) {
}
