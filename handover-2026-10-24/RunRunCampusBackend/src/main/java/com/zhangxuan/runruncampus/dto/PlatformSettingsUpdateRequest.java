package com.zhangxuan.runruncampus.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 平台营业与履约设置更新请求参数；保留入口的字段、构造器及校验契约。 */
public record PlatformSettingsUpdateRequest(
        @NotBlank @Size(max = 80) String businessHours,
        @Min(1) @Max(1440) int waitMinutes,
        @Min(1) @Max(1440) int deliveryMinutes,
        @Min(1) @Max(10) int maxActiveOrders,
        @Min(1) @Max(1440) int issuePendingMinutes,
        @Min(1) @Max(1440) int issueProcessingMinutes) {}
