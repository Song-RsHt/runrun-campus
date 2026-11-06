package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.vo.AdminUserItem;
import com.zhangxuan.runruncampus.vo.AdminUserPageResponse;




















/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface AdminUserService {

    AdminUserItem suspend(AppUser administrator, Long id);

    AdminUserItem restore(AppUser administrator, Long id);

    AdminUserItem unlockLogin(AppUser administrator, Long id);

    AdminUserItem resetPassword(AppUser administrator, Long id, String password);

    AdminUserPageResponse list(String query, UserRole role, int page, int size);
}
