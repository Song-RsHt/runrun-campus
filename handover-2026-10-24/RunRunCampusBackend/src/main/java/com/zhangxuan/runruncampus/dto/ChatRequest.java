package com.zhangxuan.runruncampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(@NotBlank(message = "请输入消息") @Size(max = 500, message = "消息不能超过500字") String content) {
}
