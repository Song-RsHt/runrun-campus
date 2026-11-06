package com.zhangxuan.runruncampus.dto.privacy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 当前政策版本、用户已同意版本及同意状态请求参数；保留入口的字段、构造器及校验契约。 */
public record PrivacyConsentRequest(@NotBlank @Size(max = 32) String version) {}
