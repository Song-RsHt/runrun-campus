package com.zhangxuan.runruncampus.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;





public record LocationUpdateRequest(@DecimalMin(value = "-90.0") @DecimalMax(value = "90.0") double latitude, @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0") double longitude) {
}
