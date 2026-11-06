package com.zhangxuan.runruncampus.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 订单创建与状态管理生命周期理由请求参数；保留入口的字段、构造器及校验契约。 */
public record OrderLifecycleReasonRequest(@NotBlank @Size(min = 5, max = 200) String reason) {}
