package com.zhangxuan.runruncampus.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 变更密码请求参数；保留入口的字段、构造器及校验契约。 */
public record ChangePasswordRequest(
        @NotBlank(message = "请输入当前密码") @Size(max = 72, message = "当前密码过长") String currentPassword,
        @NotBlank(message = "请输入新密码") @Size(min = 6, max = 72, message = "密码长度应为 6 到 72 位")
                String newPassword) {}
