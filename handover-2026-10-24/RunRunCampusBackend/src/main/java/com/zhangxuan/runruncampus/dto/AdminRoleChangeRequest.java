package com.zhangxuan.runruncampus.dto;

import com.zhangxuan.runruncampus.enums.AdminRole;

import jakarta.validation.constraints.NotNull;

/** 管理员角色变更请求参数；保留入口的字段、构造器及校验契约。 */
public record AdminRoleChangeRequest(@NotNull(message = "请选择管理员岗位") AdminRole adminRole) {}
