package com.zhangxuan.runruncampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;






public record LoginRequest(@Pattern(regexp = "^1\\d{10}$", message = "请输入 11 位手机号") String phone, @NotBlank(message = "请输入密码") String password, @Size(max = 64) String captchaToken, @Size(max = 8) String captchaCode, @Size(max = 16) String clientType, @Size(max = 80) String deviceName) {

    public LoginRequest(String phone, String password) {
        this(phone, password, null, null, null, null);
    }
}
