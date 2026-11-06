package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.vo.AdminDashboardResponse;
















/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface AdminDashboardService {

    AdminDashboardResponse overview();
}
