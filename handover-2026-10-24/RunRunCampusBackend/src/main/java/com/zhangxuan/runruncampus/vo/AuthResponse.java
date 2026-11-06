package com.zhangxuan.runruncampus.vo;

/** 旧系统账号与会话响应；保持现有 JSON 字段及序列化契约。 */
public record AuthResponse(String token, UserResponse user) {}
