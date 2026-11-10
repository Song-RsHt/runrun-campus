package com.zhangxuan.runrunv2.integration.wechat;

/** 微信网关组件；保持当前应用的配置与协作边界。 */
public interface WechatGateway {

    String exchangeLoginCode(String code);

    String exchangePhoneCode(String code);
}
