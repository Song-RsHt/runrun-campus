package com.zhangxuan.runruncampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PrivacyConsentRequest(@NotBlank @Size(max = 32) String version) {
}
