package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.Session;
import com.zhangxuan.runruncampus.enums.AdminPermission;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.model.SessionMetadata;
import com.zhangxuan.runruncampus.vo.SessionInfoVO;

import java.util.List;

/** 旧系统账号与会话业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface AuthService {

    /**
     * 创建旧系统账号，记录隐私同意并签发会话。
     *
     * @param phone 登录或目标账号手机号
     * @param password 本次密码校验或设置所需的原文，不得记录
     * @param displayName 账号展示名称
     * @param role 业务角色或管理员岗位
     * @return 新签发的会话及其所属账号
     */
    Session register(String phone, String password, String displayName, UserRole role);

    /**
     * 创建旧系统账号，记录隐私同意并签发会话。
     *
     * @param phone 登录或目标账号手机号
     * @param password 本次密码校验或设置所需的原文，不得记录
     * @param displayName 账号展示名称
     * @param role 业务角色或管理员岗位
     * @param metadata 会话设备及客户端信息
     * @return 新签发的会话及其所属账号
     */
    Session register(
            String phone,
            String password,
            String displayName,
            UserRole role,
            SessionMetadata metadata);

    /**
     * 创建旧系统账号，记录隐私同意并签发会话。
     *
     * @param phone 登录或目标账号手机号
     * @param password 本次密码校验或设置所需的原文，不得记录
     * @param displayName 账号展示名称
     * @param role 业务角色或管理员岗位
     * @param metadata 会话设备及客户端信息
     * @param privacyPolicyVersion 用户已阅读并同意的隐私政策版本
     * @return 新签发的会话及其所属账号
     */
    Session register(
            String phone,
            String password,
            String displayName,
            UserRole role,
            SessionMetadata metadata,
            String privacyPolicyVersion);

    /**
     * 校验身份后签发会话或后续绑定所需的临时授权。
     *
     * @param phone 登录或目标账号手机号
     * @param password 本次密码校验或设置所需的原文，不得记录
     * @return 新签发的会话及其所属账号
     */
    Session login(String phone, String password);

    /**
     * 校验身份后签发会话或后续绑定所需的临时授权。
     *
     * @param phone 登录或目标账号手机号
     * @param password 本次密码校验或设置所需的原文，不得记录
     * @param captchaToken 一次性验证码凭证
     * @param captchaCode 用户提交的验证码
     * @param metadata 会话设备及客户端信息
     * @return 新签发的会话及其所属账号
     */
    Session login(
            String phone,
            String password,
            String captchaToken,
            String captchaCode,
            SessionMetadata metadata);

    /**
     * 验证访问凭证并读取可用账号。
     *
     * @param authorization 入口传入的访问凭证
     * @return 通过所需身份或权限检查的账号
     */
    AppUser requireUser(String authorization);

    /**
     * 验证会话及管理员身份。
     *
     * @param authorization 入口传入的访问凭证
     * @return 通过所需身份或权限检查的账号
     */
    AppUser requireAdmin(String authorization);

    /**
     * 验证管理员会话和指定岗位权限。
     *
     * @param authorization 入口传入的访问凭证
     * @param permission 本次管理员操作要求的岗位权限
     * @return 通过所需身份或权限检查的账号
     */
    AppUser requirePermission(String authorization, AdminPermission permission);

    /**
     * 列出当前账号的有效会话和设备信息。
     *
     * @param authorization 入口传入的访问凭证
     * @return 会话信息列表，顺序和筛选沿用原查询
     */
    List<SessionInfoVO> sessions(String authorization);

    /**
     * 撤销当前账号指定的会话。
     *
     * @param authorization 入口传入的访问凭证
     * @param sessionId 待撤销会话标识
     */
    void revokeSession(String authorization, Long sessionId);

    /**
     * 撤销当前会话以外的全部会话。
     *
     * @param authorization 入口传入的访问凭证
     * @return 成功撤销的其他会话数量
     */
    long revokeOtherSessions(String authorization);

    /**
     * 撤销当前访问凭证所对应的会话。
     *
     * @param authorization 入口传入的访问凭证
     */
    void logout(String authorization);

    /**
     * 核对当前密码后更新密码并撤销原有会话。
     *
     * <p>当前密码错误时拒绝；成功后撤销原有会话，不保留隐含例外。
     *
     * @param authorization 入口传入的访问凭证
     * @param currentPassword 当前密码，仅用于校验
     * @param newPassword 新密码，仅用于安全编码和保存
     */
    void changeOwnPassword(String authorization, String currentPassword, String newPassword);
}
