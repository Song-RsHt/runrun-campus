package com.zhangxuan.runruncampus.service.admin;

import com.zhangxuan.runruncampus.entity.user.AppUser;
import com.zhangxuan.runruncampus.enums.feedback.FeedbackHandlingStatus;
import com.zhangxuan.runruncampus.enums.feedback.FeedbackType;
import com.zhangxuan.runruncampus.vo.admin.FeedbackItem;
import com.zhangxuan.runruncampus.vo.admin.FeedbackPageResponse;

/** 管理员反馈处理业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface AdminFeedbackService {

    /**
     * 查询管理员反馈处理列表，保留现有筛选和分页口径。
     *
     * @param query 可选搜索关键词
     * @param type 本次操作的业务分类
     * @param handlingStatus 处理状态筛选
     * @param page 从零开始的分页页码
     * @param size 每页记录数量
     * @return 用户反馈分页
     */
    FeedbackPageResponse list(
            String query,
            FeedbackType type,
            FeedbackHandlingStatus handlingStatus,
            int page,
            int size);

    /**
     * 领取管理员反馈处理待处理记录。
     *
     * @param administrator 已通过权限校验的管理员
     * @param id 目标业务记录标识
     * @return 用户反馈条目
     */
    FeedbackItem claim(AppUser administrator, Long id);

    /**
     * 完成管理员反馈处理处理并保存处理结论。
     *
     * @param administrator 已通过权限校验的管理员
     * @param id 目标业务记录标识
     * @param resolution 管理员处理结论
     * @return 用户反馈条目
     */
    FeedbackItem resolve(AppUser administrator, Long id, String resolution);
}
