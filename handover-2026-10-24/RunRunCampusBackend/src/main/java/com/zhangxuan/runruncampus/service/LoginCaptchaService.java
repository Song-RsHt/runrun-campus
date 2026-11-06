package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.vo.LoginCaptchaVO;

/** 登录图形验证码业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface LoginCaptchaService {

    /**
     * 生成一次性登录验证码及其校验凭证。
     *
     * @return 验证码图片和一次性校验凭证
     */
    LoginCaptchaVO issue();

    /**
     * 生成一次性登录验证码及其校验凭证。
     *
     * @param answer 验证码校验答案
     * @return 验证码图片和一次性校验凭证
     */
    LoginCaptchaVO issue(String answer);

    /**
     * 消费一次性验证码，拒绝过期、错误和重复使用。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param answer 验证码校验答案
     */
    void verify(String token, String answer);
}
