package com.zhangxuan.runrunv2.dto;

public record BindPhoneRequest(String loginTicket, String phoneCode, String requestedRole) {
}
