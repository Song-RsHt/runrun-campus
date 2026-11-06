package com.zhangxuan.runruncampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CertificationRejectionRequest(@NotBlank(message = "请输入驳回原因") @Size(max = 300, message = "驳回原因不能超过300字") String reason) {
}
