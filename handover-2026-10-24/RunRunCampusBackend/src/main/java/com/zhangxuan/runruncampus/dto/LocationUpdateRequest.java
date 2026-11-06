package com.zhangxuan.runruncampus.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/** 位置更新请求参数；保留入口的字段、构造器及校验契约。 */
public record LocationUpdateRequest(
        @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0") double latitude,
        @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0") double longitude) {}
