package com.zhangxuan.runruncampus.dto.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 订单参与者聊天请求参数；保留入口的字段、构造器及校验契约。 */
public record OrderChatRequest(
        @NotBlank(message = "请输入消息") @Size(max = 500, message = "消息不能超过500字") String content) {}
