package com.zhangxuan.runruncampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminMfaVerifyRequest(@NotBlank @Size(max = 128) String challengeToken, @NotBlank @Size(max = 128) String code, @Size(max = 16) String clientType, @Size(max = 80) String deviceName) {

    public AdminMfaVerifyRequest(String challengeToken, String code) {
        this(challengeToken, code, null, null);
    }
}
