package com.zhangxuan.runruncampus.service.privacy;

import com.zhangxuan.runruncampus.entity.user.AppUser;
import com.zhangxuan.runruncampus.vo.privacy.PrivacyConsentVO;
import com.zhangxuan.runruncampus.vo.privacy.PrivacyPolicyVO;

/** 隐私同意及账号注销业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface PrivacyService {

    /**
     * 读取当前生效的隐私政策或服务策略。
     *
     * @return 当前隐私政策的版本、生效日期及正文
     */
    PrivacyPolicyVO policy();

    /**
     * 查询用户是否已同意当前版本隐私政策。
     *
     * @param user 当前已识别账号
     * @return 当前政策版本、用户已同意版本及同意状态
     */
    PrivacyConsentVO status(AppUser user);

    /**
     * 记录用户对当前版本隐私政策的同意。
     *
     * @param user 当前已识别账号
     * @param version 用户已阅读并同意的隐私政策版本
     * @return 当前政策版本、用户已同意版本及同意状态
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
