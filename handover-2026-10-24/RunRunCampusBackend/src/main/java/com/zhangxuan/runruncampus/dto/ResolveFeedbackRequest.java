package com.zhangxuan.runruncampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 处理结论反馈请求参数；保留入口的字段、构造器及校验契约。 */
public record ResolveFeedbackRequest(
        @NotBlank(message = "请输入处理结果") @Size(max = 500, message = "处理结果不能超过500字")
                String resolution) {}
