package com.zhangxuan.runrunv2;

public interface WechatGateway {
    String exchangeLoginCode(String code);
    String exchangePhoneCode(String code);
}
