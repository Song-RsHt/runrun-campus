package com.zhangxuan.runruncampus.dto.request.admin.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectManualPaymentRequest(
    @NotBlank(message = "请输入驳回原因") @Size(max = 200, message = "驳回原因不能超过200字") String reason) {}
