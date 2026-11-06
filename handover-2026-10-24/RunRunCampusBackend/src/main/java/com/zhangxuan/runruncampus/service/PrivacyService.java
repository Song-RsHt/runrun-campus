package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.vo.PrivacyConsentVO;
import com.zhangxuan.runruncampus.vo.PrivacyPolicyVO;



























/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface PrivacyService {

    PrivacyPolicyVO policy();

    PrivacyConsentVO status(AppUser user);

    PrivacyConsentVO consent(AppUser user, String version);

    void withdrawConsent(AppUser current);

    void deleteAccount(AppUser current, String password, boolean confirmed);
}
