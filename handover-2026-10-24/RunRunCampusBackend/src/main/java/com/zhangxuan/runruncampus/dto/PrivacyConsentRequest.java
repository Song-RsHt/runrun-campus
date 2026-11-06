package com.zhangxuan.runruncampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 隐私同意及账号注销同意请求参数；保留入口的字段、构造器及校验契约。 */
public record PrivacyConsentRequest(@NotBlank @Size(max = 32) String version) {}
