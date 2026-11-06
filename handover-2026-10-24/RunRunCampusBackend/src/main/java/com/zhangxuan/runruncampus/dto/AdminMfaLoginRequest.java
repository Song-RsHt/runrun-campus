package com.zhangxuan.runruncampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminMfaLoginRequest(@NotBlank @Pattern(regexp = "^1\\d{10}$") String phone, @NotBlank @Size(max = 72) String password) {
}
