package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.model.Settings;












/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface PlatformSettingsService {

    Settings get();

    Settings update(AppUser administrator, Settings requested);
}
