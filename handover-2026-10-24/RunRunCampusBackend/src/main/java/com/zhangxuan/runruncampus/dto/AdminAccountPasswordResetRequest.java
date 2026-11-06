package com.zhangxuan.runruncampus.dto;

import jakarta.validation.constraints.Size;

/** 管理员账号密码重置请求参数；保留入口的字段、构造器及校验契约。 */
public record AdminAccountPasswordResetRequest(
        @Size(min = 8, max = 72, message = "管理员密码长度应为 8 到 72 位") String password) {}
