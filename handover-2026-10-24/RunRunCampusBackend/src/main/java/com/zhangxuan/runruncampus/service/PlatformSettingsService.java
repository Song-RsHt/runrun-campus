package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.vo.PlatformSettingsVO;

/** 平台营业与履约设置业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface PlatformSettingsService {

    /**
     * 读取平台营业与履约设置详情。
     *
     * @return 平台运行设置
     */
    PlatformSettingsVO get();

    /**
     * 更新平台营业与履约设置记录，保持原有权限及并发条件。
     *
     * @param administrator 已通过权限校验的管理员
     * @param requested 请求更新的平台设置
     * @return 平台运行设置
     */
    PlatformSettingsVO update(AppUser administrator, PlatformSettingsVO requested);
}
