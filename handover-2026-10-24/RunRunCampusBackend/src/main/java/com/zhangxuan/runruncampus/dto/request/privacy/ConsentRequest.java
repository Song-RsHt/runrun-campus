package com.zhangxuan.runruncampus.dto.request.privacy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConsentRequest(@NotBlank @Size(max = 32) String version) {}
