package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.enums.AdminAuditAction;
import com.zhangxuan.runruncampus.enums.AdminAuditTargetType;
import com.zhangxuan.runruncampus.vo.AdminAuditPageResponse;

/** 管理员操作审计业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface AdminAuditService {

    /**
     * 登记管理员操作审计事件及必要的操作来源。
     *
     * @param administrator 已通过权限校验的管理员
     * @param action 管理员审计动作
     * @param targetType 审计目标类型
     * @param targetId 目标业务记录标识
     * @param summary 脱敏后的审计摘要
     */
    void record(
            AppUser administrator,
            AdminAuditAction action,
            AdminAuditTargetType targetType,
            Long targetId,
            String summary);

    /**
     * 查询管理员操作审计列表，保留现有筛选和分页口径。
     *
     * @param query 可选搜索关键词
     * @param action 管理员审计动作
     * @param page 从零开始的分页页码
     * @param size 每页记录数量
     * @return 管理员操作审计分页
     */
    AdminAuditPageResponse list(String query, AdminAuditAction action, int page, int size);
}
