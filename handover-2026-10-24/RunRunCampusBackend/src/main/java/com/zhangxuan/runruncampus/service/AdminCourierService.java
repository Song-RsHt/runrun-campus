package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.enums.CourierCertificationStatus;
import com.zhangxuan.runruncampus.vo.AdminCourierPageResponse;
import com.zhangxuan.runruncampus.vo.AdminCourierPerformanceResponse;
































/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface AdminCourierService {

    AdminCourierPageResponse list(String query, CourierCertificationStatus status, int page, int size);

    AdminCourierPerformanceResponse performance(Long courierId, int page, int size);
}
