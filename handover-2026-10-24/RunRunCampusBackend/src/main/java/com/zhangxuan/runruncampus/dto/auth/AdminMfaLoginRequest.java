package com.zhangxuan.runruncampus.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 管理员双因素认证登录请求参数；保留入口的字段、构造器及校验契约。 */
public record AdminMfaLoginRequest(
        @NotBlank @Pattern(regexp = "^1\\d{10}$") String phone,
        @NotBlank @Size(max = 72) String password) {}
