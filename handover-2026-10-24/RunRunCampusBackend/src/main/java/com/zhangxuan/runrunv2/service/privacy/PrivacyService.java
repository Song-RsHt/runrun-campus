package com.zhangxuan.runrunv2.service.privacy;

import com.zhangxuan.runrunv2.vo.privacy.PrivacyPolicyVO;

/** v2 协议读取及同意回执；不访问旧系统数据。 */
public interface PrivacyService {
    /** 返回沿用已批准正文的当前协议。 */
    PrivacyPolicyVO policy();

    /** 校验当前会话并持久化该账号明确同意的版本，重复同意不重复记账。 */
    void consent(String token, String version);
}
