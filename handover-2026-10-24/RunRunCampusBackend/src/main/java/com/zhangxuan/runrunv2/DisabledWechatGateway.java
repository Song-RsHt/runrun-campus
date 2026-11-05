package com.zhangxuan.runrunv2;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Local fixtures must never exchange codes with real WeChat. */
@Component
@ConditionalOnProperty(name="rrc.identity.wechat-enabled", havingValue="false")
public class DisabledWechatGateway implements WechatGateway {
    public String exchangeLoginCode(String code) { throw new IdentityFailure(401, "WECHAT_AUTH_FAILED"); }
    public String exchangePhoneCode(String code) { throw new IdentityFailure(401, "WECHAT_AUTH_FAILED"); }
}
