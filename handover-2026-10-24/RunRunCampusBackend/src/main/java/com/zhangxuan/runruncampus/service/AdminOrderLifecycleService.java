package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;























/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface AdminOrderLifecycleService {

    void forceComplete(AppUser administrator, Long id, String reason);

    void archive(AppUser administrator, Long id, String reason);

    void unarchive(AppUser administrator, Long id);
}
