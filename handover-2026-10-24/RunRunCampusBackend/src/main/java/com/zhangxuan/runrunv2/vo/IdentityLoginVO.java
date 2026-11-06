package com.zhangxuan.runrunv2.vo;

/** 新系统登录响应响应；保持现有 JSON 字段及序列化契约。 */
public record IdentityLoginVO(String loginTicket, String token, IdentityAccountVO account) {}
