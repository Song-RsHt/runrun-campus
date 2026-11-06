package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.enums.FeedbackHandlingStatus;
import com.zhangxuan.runruncampus.enums.FeedbackType;
import com.zhangxuan.runruncampus.vo.FeedbackItem;
import com.zhangxuan.runruncampus.vo.FeedbackPageResponse;




















/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface AdminFeedbackService {

    FeedbackPageResponse list(String query, FeedbackType type, FeedbackHandlingStatus handlingStatus, int page, int size);

    FeedbackItem claim(AppUser administrator, Long id);

    FeedbackItem resolve(AppUser administrator, Long id, String resolution);
}
