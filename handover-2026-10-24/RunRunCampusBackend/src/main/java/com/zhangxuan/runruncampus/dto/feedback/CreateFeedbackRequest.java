package com.zhangxuan.runruncampus.dto.feedback;

import com.zhangxuan.runruncampus.enums.feedback.FeedbackType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 创建反馈请求参数；保留入口的字段、构造器及校验契约。 */
public record CreateFeedbackRequest(
        @NotNull(message = "请选择反馈类型") FeedbackType type,
        @NotBlank(message = "请输入反馈内容") @Size(max = 500, message = "反馈内容不能超过500字") String content) {}
