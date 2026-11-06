package com.zhangxuan.runruncampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 信用分及风险信号理由请求参数；保留入口的字段、构造器及校验契约。 */
public record CreditReasonRequest(@NotBlank @Size(min = 5, max = 300) String reason) {}
