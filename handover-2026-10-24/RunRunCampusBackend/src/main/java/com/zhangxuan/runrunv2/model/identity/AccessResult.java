package com.zhangxuan.runrunv2.model.identity;

/** 管理员访问授权结果；仅承载内部协作所需的数据或校验逻辑。 */
public record AccessResult(String userId, boolean enabled, long securityVersion) {}
