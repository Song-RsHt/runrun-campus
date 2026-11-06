package com.zhangxuan.runruncampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 管理员双因素认证核验请求参数；保留入口的字段、构造器及校验契约。 */
public record AdminMfaVerifyRequest(
        @NotBlank @Size(max = 128) String challengeToken,
        @NotBlank @Size(max = 128) String code,
        @Size(max = 16) String clientType,
        @Size(max = 80) String deviceName) {

    public AdminMfaVerifyRequest(String challengeToken, String code) {
        this(challengeToken, code, null, null);
    }
}
