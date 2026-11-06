package com.zhangxuan.runruncampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 管理员用户维护密码重置请求参数；保留入口的字段、构造器及校验契约。 */
public record AdminUserPasswordResetRequest(
        @NotBlank(message = "请输入新密码") @Size(min = 6, max = 72, message = "密码长度应为 6 到 72 位")
                String password) {}
