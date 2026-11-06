package com.zhangxuan.runruncampus.dto;

import jakarta.validation.constraints.NotNull;

/** 派单请求参数；保留入口的字段、构造器及校验契约。 */
public record DispatchRequest(@NotNull Long courierId) {}
