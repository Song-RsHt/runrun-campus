package com.zhangxuan.runruncampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 管理员双因素认证替换请求参数；保留入口的字段、构造器及校验契约。 */
public record AdminMfaReplaceRequest(
        @NotBlank @Size(max = 72) String password, @NotBlank @Size(max = 128) String code) {}
