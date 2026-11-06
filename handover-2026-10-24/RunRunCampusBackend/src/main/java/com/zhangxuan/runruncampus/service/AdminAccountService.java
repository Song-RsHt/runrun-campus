package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.enums.AdminRole;
import com.zhangxuan.runruncampus.vo.AdminAccountItem;
import java.util.List;

















/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface AdminAccountService {

    List<AdminAccountItem> list();

    AdminAccountItem create(AppUser creator, String phone, String password, String displayName, AdminRole role);

    AdminAccountItem changeRole(AppUser operator, Long id, AdminRole role);

    AdminAccountItem suspend(AppUser operator, Long id);

    AdminAccountItem restore(AppUser operator, Long id);

    AdminAccountItem resetPassword(AppUser operator, Long id, String password);

    AdminAccountItem unlockLogin(AppUser operator, Long id);

    void changeOwnPassword(AppUser administrator, String currentPassword, String newPassword);
}
