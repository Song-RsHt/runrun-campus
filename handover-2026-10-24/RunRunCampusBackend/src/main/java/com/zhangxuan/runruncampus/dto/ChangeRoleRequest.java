package com.zhangxuan.runruncampus.dto;

import com.zhangxuan.runruncampus.enums.AdminRole;
import jakarta.validation.constraints.NotNull;





public record ChangeRoleRequest(@NotNull(message = "请选择管理员岗位") AdminRole adminRole) {
}
