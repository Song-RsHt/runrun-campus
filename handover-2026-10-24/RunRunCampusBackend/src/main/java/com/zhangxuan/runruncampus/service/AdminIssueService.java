package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.enums.OrderIssueHandlingStatus;
import com.zhangxuan.runruncampus.enums.OrderIssueType;
import com.zhangxuan.runruncampus.vo.AdminIssueItem;
import com.zhangxuan.runruncampus.vo.AdminIssuePageResponse;

























/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface AdminIssueService {

    AdminIssuePageResponse list(String query, OrderIssueType type, OrderIssueHandlingStatus handlingStatus, int page, int size);

    AdminIssuePageResponse list(String query, OrderIssueType type, OrderIssueHandlingStatus handlingStatus, boolean overdueOnly, int page, int size);

    AdminIssueItem claim(AppUser administrator, Long id);

    AdminIssueItem resolve(AppUser administrator, Long id, String resolution);
}
