package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.enums.CourierCertificationStatus;
import com.zhangxuan.runruncampus.enums.FeedbackHandlingStatus;
import com.zhangxuan.runruncampus.enums.OrderIssueHandlingStatus;
import com.zhangxuan.runruncampus.enums.OrderStatus;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.repository.CourierCertificationRepository;
import com.zhangxuan.runruncampus.repository.FeedbackRepository;
import com.zhangxuan.runruncampus.repository.OrderIssueRepository;
import com.zhangxuan.runruncampus.repository.OrderRepository;
import com.zhangxuan.runruncampus.repository.UserRepository;
import com.zhangxuan.runruncampus.vo.AdminDashboardResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface AdminDashboardService {

    AdminDashboardResponse overview();
}
