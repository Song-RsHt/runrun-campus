package com.zhangxuan.runruncampus.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 账号删除请求参数；保留入口的字段、构造器及校验契约。 */
public record AccountDeletionRequest(
        @NotBlank(message = "请输入当前密码") @Size(max = 72) String password,
        @AssertTrue(message = "请确认注销账号") boolean confirmed) {}
