package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.vo.LoginCaptchaVO;




















/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface LoginCaptchaService {

    LoginCaptchaVO issue();

    LoginCaptchaVO issue(String answer);

    void verify(String token, String answer);
}
