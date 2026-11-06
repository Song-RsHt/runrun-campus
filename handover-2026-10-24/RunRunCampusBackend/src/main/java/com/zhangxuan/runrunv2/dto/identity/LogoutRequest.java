package com.zhangxuan.runrunv2.dto.identity;

/** 登出请求参数；保留入口的字段、构造器及校验契约。 */
public record LogoutRequest(boolean allSessions) {}
