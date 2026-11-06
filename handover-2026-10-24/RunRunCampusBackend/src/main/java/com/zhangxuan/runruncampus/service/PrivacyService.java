package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.vo.PrivacyConsentVO;
import com.zhangxuan.runruncampus.vo.PrivacyPolicyVO;

/** 隐私同意及账号注销业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface PrivacyService {

    /**
     * 读取当前生效的隐私政策或服务策略。
     *
     * @return 隐私同意及账号注销策略
     */
    PrivacyPolicyVO policy();

    /**
     * 查询隐私同意及账号注销当前状态。
     *
     * @param user 当前已识别账号
     * @return 隐私同意及账号注销同意
     */
    PrivacyConsentVO status(AppUser user);

    /**
     * 记录用户对当前版本隐私政策的同意。
     *
     * @param user 当前已识别账号
     * @param version 用户已阅读并同意的隐私政策版本
     * @return 隐私同意及账号注销同意
     */
    PrivacyConsentVO consent(AppUser user, String version);

    /**
     * 撤回隐私同意并按原规则撤销会话。
     *
     * @param current 当前已识别账号
     */
    void withdrawConsent(AppUser current);

    /**
     * 确认密码和业务阻塞条件后注销账号，保留必要历史与审计。
     *
     * <p>须确认注销并核对密码；进行中订单或未完成退款会阻止注销。
     *
     * @param current 当前已识别账号
     * @param password 本次密码校验或设置所需的原文，不得记录
     * @param confirmed 是否已明确确认注销账号
     */
    void deleteAccount(AppUser current, String password, boolean confirmed);
}
