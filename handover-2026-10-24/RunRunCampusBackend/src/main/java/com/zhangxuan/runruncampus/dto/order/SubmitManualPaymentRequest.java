package com.zhangxuan.runruncampus.dto.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 提交人工支付请求参数；保留入口的字段、构造器及校验契约。 */
public record SubmitManualPaymentRequest(
        @NotBlank(message = "请输入付款账号备注") @Size(max = 64, message = "付款账号备注不能超过64字")
                String payerReference) {}
