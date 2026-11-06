package com.zhangxuan.runruncampus.dto.request.admin.account;

import com.zhangxuan.runruncampus.enums.admin.AdminRole;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull(message = "请选择管理员岗位") AdminRole adminRole) {}
