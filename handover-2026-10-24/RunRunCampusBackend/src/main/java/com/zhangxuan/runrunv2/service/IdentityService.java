package com.zhangxuan.runrunv2.service;

import com.zhangxuan.runrunv2.model.IdentityUser;
import com.zhangxuan.runrunv2.vo.Account;
import com.zhangxuan.runrunv2.vo.LoginResult;






/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface IdentityService {

    LoginResult login(String code);

    LoginResult bindPhone(String loginTicket, String phoneCode, String requestedRole);

    LoginResult passwordLogin(String phone, String password);

    Account authenticate(String token);

    Account requireRole(String token, String role, boolean needsCourierEligibility);

    void revoke(String token, boolean all, String key);

    void setPassword(String token, String password, String key);

    /**
     * Business writes must invoke this inside their own transaction and hold the account lock to
     * commit.
     */
    Account authorizeForWrite(String token, String role, boolean courierEligibility);

    IdentityUser user(long id, boolean locked);

    void lock(String key);
}
