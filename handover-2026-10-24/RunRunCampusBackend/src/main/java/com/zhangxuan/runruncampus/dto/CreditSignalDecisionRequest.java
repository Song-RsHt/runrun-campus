package com.zhangxuan.runruncampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 信用分及风险信号信号决定请求参数；保留入口的字段、构造器及校验契约。 */
public record CreditSignalDecisionRequest(
        @NotBlank String decision,
        long expectedVersion,
        String photoResourceId,
        @Size(max = 300) String reason) {}
