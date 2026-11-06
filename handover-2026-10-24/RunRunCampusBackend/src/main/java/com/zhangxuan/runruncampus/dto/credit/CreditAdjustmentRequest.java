package com.zhangxuan.runruncampus.dto.credit;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 信用分及风险信号调整请求参数；保留入口的字段、构造器及校验契约。 */
public record CreditAdjustmentRequest(
        @Min(-100) @Max(100) int delta, @NotBlank @Size(min = 5, max = 300) String reason) {}
