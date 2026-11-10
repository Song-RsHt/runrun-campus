package com.zhangxuan.runrunv2.dto.identity;

/** 密码登录请求参数；保留入口的字段、构造器及校验契约。 */
public record PasswordLoginRequest(String phone, String password) {}
