package com.zhangxuan.runrunv2.dto.response.identity;

public record LoginResult(String loginTicket, String token, Account account) {}
