package com.zhangxuan.runruncampus.dto.order;

import com.zhangxuan.runruncampus.enums.order.OrderIssueType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 创建异常工单请求参数；保留入口的字段、构造器及校验契约。 */
public record CreateIssueRequest(
        @NotNull(message = "请选择异常类型") OrderIssueType type,
        @NotBlank(message = "请输入异常说明") @Size(max = 500, message = "异常说明不能超过500字")
                String description) {}
