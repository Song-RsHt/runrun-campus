package com.zhangxuan.runrunv2.dto;

/** 绑定手机号请求参数；保留入口的字段、构造器及校验契约。 */
public record BindPhoneRequest(String loginTicket, String phoneCode, String requestedRole) {}
