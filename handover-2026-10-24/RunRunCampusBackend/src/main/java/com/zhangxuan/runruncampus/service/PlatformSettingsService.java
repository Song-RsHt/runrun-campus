package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.vo.PlatformSettingsVO;
















/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface PlatformSettingsService {

    PlatformSettingsVO get();

    PlatformSettingsVO update(AppUser administrator, PlatformSettingsVO requested);
}
