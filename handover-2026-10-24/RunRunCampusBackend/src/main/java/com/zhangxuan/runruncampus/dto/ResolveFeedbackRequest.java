package com.zhangxuan.runruncampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResolveFeedbackRequest(@NotBlank(message = "请输入处理结果") @Size(max = 500, message = "处理结果不能超过500字") String resolution) {
}
