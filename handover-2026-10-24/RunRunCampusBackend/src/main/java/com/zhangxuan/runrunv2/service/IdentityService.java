package com.zhangxuan.runrunv2.service;

import com.zhangxuan.runrunv2.model.IdentityUser;
import com.zhangxuan.runrunv2.vo.IdentityAccountVO;
import com.zhangxuan.runrunv2.vo.IdentityLoginVO;

/** 新系统身份与会话业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface IdentityService {

    /**
     * 使用微信登录码建立新系统会话或手机号绑定票据。
     *
     * @param code 微信服务端签发的临时登录码
     * @return 新系统登录
     */
    IdentityLoginVO login(String code);

    /**
     * 用微信服务端手机号完成临时登录票据绑定。
     *
     * @param loginTicket 微信登录后的一次性手机号绑定票据
     * @param phoneCode 微信服务端手机号授权码
     * @param requestedRole 首次绑定时申请的业务角色
     * @return 新系统登录
     */
    IdentityLoginVO bindPhone(String loginTicket, String phoneCode, String requestedRole);

    /**
     * 验证新系统账号密码并签发会话。
     *
     * @param phone 登录或目标账号手机号
     * @param password 本次密码校验或设置所需的原文，不得记录
     * @return 新系统登录
     */
    IdentityLoginVO passwordLogin(String phone, String password);

    /**
     * 验证新系统会话与账号访问版本，不改变账号身份。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @return 新系统账号
     */
    IdentityAccountVO authenticate(String token);

    /**
     * 校验角色，以及调用方要求的当前骑手接单资格。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param role 业务角色或管理员岗位
     * @param needsCourierEligibility 是否要求当前骑手具备有效接单资格
     * @return 新系统账号
     */
    IdentityAccountVO requireRole(String token, String role, boolean needsCourierEligibility);

    /**
     * 按原幂等作用域撤销当前或全部会话。
     *
     * <p>单会话撤销的幂等作用域绑定到被撤销会话；其他会话复用同键必须冲突。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param all 是否撤销当前账号全部会话
     * @param key 本次操作的幂等键，作用域由现有业务规则决定
     */
    void revoke(String token, boolean all, String key);

    /**
     * 设置密码并撤销包括调用方在内的全部会话。
     *
     * <p>调用方会话也被撤销；旧凭证不能继续操作。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param password 本次密码校验或设置所需的原文，不得记录
     * @param key 本次操作的幂等键，作用域由现有业务规则决定
     */
    void setPassword(String token, String password, String key);

    /**
     * 在业务事务内验证角色并锁账号，将锁保持到调用方提交。
     *
     * <p>调用方必须已开启业务事务；账号锁持有至提交，权限或访问版本失效时拒绝写入。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param role 业务角色或管理员岗位
     * @param courierEligibility 是否要求当前骑手具备有效接单资格
     * @return 新系统账号
     */
    IdentityAccountVO authorizeForWrite(String token, String role, boolean courierEligibility);

    /**
     * 读取内部账号记录，调用方可要求持有行锁。
     *
     * @param id 目标业务记录标识
     * @param locked 是否使用行锁读取账号
     * @return 内部账号记录及其安全版本
     */
    IdentityUser user(long id, boolean locked);

    /**
     * 按键获取身份业务事务锁，锁由调用方事务持有至提交。
     *
     * @param key 本次操作的幂等键，作用域由现有业务规则决定
     */
    void lock(String key);
}
