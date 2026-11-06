package com.zhangxuan.runruncampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 骑手无法履约请求参数；保留入口的字段、构造器及校验契约。 */
public record CourierUnableRequest(
        @NotBlank(message = "请输入无法履约原因") @Size(max = 300, message = "原因不能超过300字") String reason) {}
