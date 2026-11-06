package com.zhangxuan.runruncampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 完成订单请求参数；保留入口的字段、构造器及校验契约。 */
public record CompleteOrderRequest(
        @NotBlank(message = "请输入收货码") @Pattern(regexp = "^[0-9]{6}$", message = "请输入 6 位数字收货码")
                String deliveryCode) {}
