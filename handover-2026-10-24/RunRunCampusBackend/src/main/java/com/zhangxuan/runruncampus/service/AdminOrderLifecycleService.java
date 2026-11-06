package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;

/** 管理员订单收尾与归档业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface AdminOrderLifecycleService {

    /**
     * 按管理员权限强制完成订单，保存理由与事件。
     *
     * @param administrator 已通过权限校验的管理员
     * @param id 目标业务记录标识
     * @param reason 业务操作理由，按原有长度和必填规则校验
     */
    void forceComplete(AppUser administrator, Long id, String reason);

    /**
     * 将允许归档的订单标为归档并记录原因。
     *
     * @param administrator 已通过权限校验的管理员
     * @param id 目标业务记录标识
     * @param reason 业务操作理由，按原有长度和必填规则校验
     */
    void archive(AppUser administrator, Long id, String reason);

    /**
     * 恢复订单的未归档展示状态。
     *
     * @param administrator 已通过权限校验的管理员
     * @param id 目标业务记录标识
     */
    void unarchive(AppUser administrator, Long id);
}
