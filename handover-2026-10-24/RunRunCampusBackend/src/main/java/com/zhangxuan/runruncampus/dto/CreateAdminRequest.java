package com.zhangxuan.runruncampus.dto;

import com.zhangxuan.runruncampus.enums.AdminRole;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 创建管理员请求参数；保留入口的字段、构造器及校验契约。 */
public record CreateAdminRequest(
        @Pattern(regexp = "^1\\d{10}$", message = "请输入 11 位手机号") String phone,
        @Size(min = 8, max = 72, message = "管理员密码长度应为 8 到 72 位") String password,
        @NotBlank(message = "请输入姓名") @Size(max = 48, message = "姓名过长") String displayName,
        @NotNull(message = "请选择管理员岗位") AdminRole adminRole) {}
