package com.zhangxuan.runruncampus.dto;

import com.zhangxuan.runruncampus.enums.ServiceType;

import jakarta.validation.constraints.NotNull;

/** 确认包裹规格请求参数；保留入口的字段、构造器及校验契约。 */
public record ConfirmPackageSizeRequest(@NotNull(message = "请选择包裹规格") ServiceType serviceType) {}
