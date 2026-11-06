package com.zhangxuan.runruncampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitManualPaymentRequest(@NotBlank(message = "请输入付款账号备注") @Size(max = 64, message = "付款账号备注不能超过64字") String payerReference) {
}
