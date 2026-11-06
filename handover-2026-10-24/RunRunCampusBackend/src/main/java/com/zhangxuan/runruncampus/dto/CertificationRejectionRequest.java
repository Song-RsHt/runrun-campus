package com.zhangxuan.runruncampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 资质驳回请求参数；保留入口的字段、构造器及校验契约。 */
public record CertificationRejectionRequest(
        @NotBlank(message = "请输入驳回原因") @Size(max = 300, message = "驳回原因不能超过300字") String reason) {}
