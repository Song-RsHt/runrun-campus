package com.zhangxuan.runruncampus.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 管理员双因素认证重置请求参数；保留入口的字段、构造器及校验契约。 */
public record AdminMfaResetRequest(@NotBlank @Size(max = 200) String reason) {}
