package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.vo.AdminUserItem;
import com.zhangxuan.runruncampus.vo.AdminUserPageResponse;

/** 管理员用户维护业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface AdminUserService {

    /**
     * 停用目标账号并撤销其现有会话。
     *
     * @param administrator 已通过权限校验的管理员
     * @param id 目标业务记录标识
     * @return 用户账号及管理状态
     */
    AdminUserItem suspend(AppUser administrator, Long id);

    /**
     * 恢复目标账号，保持原有审计规则。
     *
     * @param administrator 已通过权限校验的管理员
     * @param id 目标业务记录标识
     * @return 用户账号及管理状态
     */
    AdminUserItem restore(AppUser administrator, Long id);

    /**
     * 解除目标账号的登录锁定状态并审计。
     *
     * @param administrator 已通过权限校验的管理员
     * @param id 目标业务记录标识
     * @return 用户账号及管理状态
     */
    AdminUserItem unlockLogin(AppUser administrator, Long id);

    /**
     * 重置目标账号密码并撤销旧会话。
     *
     * @param administrator 已通过权限校验的管理员
     * @param id 目标业务记录标识
     * @param password 本次密码校验或设置所需的原文，不得记录
     * @return 用户账号及管理状态
     */
    AdminUserItem resetPassword(AppUser administrator, Long id, String password);

    /**
     * 查询管理员用户维护列表，保留现有筛选和分页口径。
     *
     * @param query 可选搜索关键词
     * @param role 业务角色或管理员岗位
     * @param page 从零开始的分页页码
     * @param size 每页记录数量
     * @return 用户分页及账号状态
     */
    AdminUserPageResponse list(String query, UserRole role, int page, int size);
}
