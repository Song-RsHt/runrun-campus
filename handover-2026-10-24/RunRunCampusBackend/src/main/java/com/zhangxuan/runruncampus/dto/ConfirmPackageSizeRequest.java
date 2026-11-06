package com.zhangxuan.runruncampus.dto;

import com.zhangxuan.runruncampus.enums.ServiceType;
import jakarta.validation.constraints.NotNull;





public record ConfirmPackageSizeRequest(@NotNull(message = "请选择包裹规格") ServiceType serviceType) {
}
