package com.zhangxuan.runrunv2.integration.wechat;

public interface WechatGateway {

  String exchangeLoginCode(String code);

  String exchangePhoneCode(String code);
}
