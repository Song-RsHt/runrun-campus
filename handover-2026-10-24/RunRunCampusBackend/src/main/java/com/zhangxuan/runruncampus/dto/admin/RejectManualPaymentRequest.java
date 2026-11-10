package com.zhangxuan.runruncampus.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 驳回人工支付请求参数；保留入口的字段、构造器及校验契约。 */
public record RejectManualPaymentRequest(
        @NotBlank(message = "请输入驳回原因") @Size(max = 200, message = "驳回原因不能超过200字") String reason) {}
