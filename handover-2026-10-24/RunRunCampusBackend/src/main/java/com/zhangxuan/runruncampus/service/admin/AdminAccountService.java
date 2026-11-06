package com.zhangxuan.runruncampus.service.admin;

import com.zhangxuan.runruncampus.entity.user.AppUser;
import com.zhangxuan.runruncampus.enums.admin.AdminRole;
import com.zhangxuan.runruncampus.vo.admin.AdminAccountItem;

import java.util.List;

/** 管理员账号业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface AdminAccountService {

    /**
     * 查询管理员账号列表，保留现有筛选和分页口径。
     *
     * @return 管理员账号条目列表，顺序和筛选沿用原查询
     */
    List<AdminAccountItem> list();

    /**
     * 创建管理员账号记录，按现有校验和持久化规则处理。
     *
     * @param creator 创建管理员账号的操作者
     * @param phone 登录或目标账号手机号
     * @param password 本次密码校验或设置所需的原文，不得记录
     * @param displayName 账号展示名称
     * @param role 业务角色或管理员岗位
     * @return 管理员账号条目
     */
    AdminAccountItem create(
            AppUser creator, String phone, String password, String displayName, AdminRole role);

    /**
     * 变更管理员岗位并同步会话撤销及审计。
     *
     * @param operator 当前管理员操作者
     * @param id 目标业务记录标识
     * @param role 业务角色或管理员岗位
     * @return 管理员账号条目
     */
    AdminAccountItem changeRole(AppUser operator, Long id, AdminRole role);

    /**
     * 停用目标账号并撤销其现有会话。
     *
     * @param operator 当前管理员操作者
     * @param id 目标业务记录标识
     * @return 管理员账号条目
     */
    AdminAccountItem suspend(AppUser operator, Long id);

    /**
     * 恢复目标账号，保持原有审计规则。
     *
     * @param operator 当前管理员操作者
     * @param id 目标业务记录标识
     * @return 管理员账号条目
     */
    AdminAccountItem restore(AppUser operator, Long id);

    /**
     * 重置目标账号密码并撤销旧会话。
     *
     * @param operator 当前管理员操作者
     * @param id 目标业务记录标识
     * @param password 本次密码校验或设置所需的原文，不得记录
     * @return 管理员账号条目
     */
    AdminAccountItem resetPassword(AppUser operator, Long id, String password);

    /**
     * 解除目标账号的登录锁定状态并审计。
     *
     * @param operator 当前管理员操作者
     * @param id 目标业务记录标识
     * @return 管理员账号条目
     */
    AdminAccountItem unlockLogin(AppUser operator, Long id);

    /**
     * 核对当前密码后更新密码并撤销原有会话。
     *
     * @param administrator 已通过权限校验的管理员
     * @param currentPassword 当前密码，仅用于校验
     * @param newPassword 新密码，仅用于安全编码和保存
     */
    void changeOwnPassword(AppUser administrator, String currentPassword, String newPassword);
}
