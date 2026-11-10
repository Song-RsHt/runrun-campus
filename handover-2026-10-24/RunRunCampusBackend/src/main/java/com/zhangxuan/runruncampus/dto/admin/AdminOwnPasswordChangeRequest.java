package com.zhangxuan.runruncampus.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 管理员本人密码变更请求参数；保留入口的字段、构造器及校验契约。 */
public record AdminOwnPasswordChangeRequest(
        @NotBlank(message = "请输入当前密码") String currentPassword,
        @Size(min = 8, max = 72, message = "新密码长度应为 8 到 72 位") String newPassword) {}
