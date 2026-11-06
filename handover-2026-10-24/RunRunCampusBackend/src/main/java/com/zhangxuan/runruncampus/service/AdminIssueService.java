package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.enums.OrderIssueHandlingStatus;
import com.zhangxuan.runruncampus.enums.OrderIssueType;
import com.zhangxuan.runruncampus.vo.AdminIssueItem;
import com.zhangxuan.runruncampus.vo.AdminIssuePageResponse;

/** 管理员异常工单业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface AdminIssueService {

    /**
     * 查询管理员异常工单列表，保留现有筛选和分页口径。
     *
     * @param query 可选搜索关键词
     * @param type 本次操作的业务分类
     * @param handlingStatus 处理状态筛选
     * @param page 从零开始的分页页码
     * @param size 每页记录数量
     * @return 管理员异常工单分页
     */
    AdminIssuePageResponse list(
            String query,
            OrderIssueType type,
            OrderIssueHandlingStatus handlingStatus,
            int page,
            int size);

    /**
     * 查询管理员异常工单列表，保留现有筛选和分页口径。
     *
     * @param query 可选搜索关键词
     * @param type 本次操作的业务分类
     * @param handlingStatus 处理状态筛选
     * @param overdueOnly 是否只查询超过原处理时限的工单
     * @param page 从零开始的分页页码
     * @param size 每页记录数量
     * @return 管理员异常工单分页
     */
    AdminIssuePageResponse list(
            String query,
            OrderIssueType type,
            OrderIssueHandlingStatus handlingStatus,
            boolean overdueOnly,
            int page,
            int size);

    /**
     * 领取管理员异常工单待处理记录。
     *
     * @param administrator 已通过权限校验的管理员
     * @param id 目标业务记录标识
     * @return 管理员异常工单条目
     */
    AdminIssueItem claim(AppUser administrator, Long id);

    /**
     * 完成管理员异常工单处理并保存处理结论。
     *
     * @param administrator 已通过权限校验的管理员
     * @param id 目标业务记录标识
     * @param resolution 管理员处理结论
     * @return 管理员异常工单条目
     */
    AdminIssueItem resolve(AppUser administrator, Long id, String resolution);
}
