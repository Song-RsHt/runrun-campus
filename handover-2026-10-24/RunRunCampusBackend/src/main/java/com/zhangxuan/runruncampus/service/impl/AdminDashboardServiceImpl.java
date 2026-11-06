package com.zhangxuan.runruncampus.service.impl;

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
import com.zhangxuan.runruncampus.service.AdminDashboardService;

@Service
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final UserRepository userRepository;

    private final OrderRepository orderRepository;

    private final CourierCertificationRepository certificationRepository;

    private final OrderIssueRepository issueRepository;

    private final FeedbackRepository feedbackRepository;

    public AdminDashboardServiceImpl(UserRepository userRepository, OrderRepository orderRepository, CourierCertificationRepository certificationRepository, OrderIssueRepository issueRepository, FeedbackRepository feedbackRepository) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.certificationRepository = certificationRepository;
        this.issueRepository = issueRepository;
        this.feedbackRepository = feedbackRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public AdminDashboardResponse overview() {
        return new AdminDashboardResponse(userRepository.count(), userRepository.countByRole(UserRole.COURIER), orderRepository.count(), orderRepository.countFulfillablePendingOrders(), orderRepository.countByStatus(OrderStatus.ACCEPTED) + orderRepository.countByStatus(OrderStatus.PICKED_UP), certificationRepository.countByStatus(CourierCertificationStatus.PENDING), issueRepository.countByHandlingStatus(OrderIssueHandlingStatus.PENDING), issueRepository.countByHandlingStatus(OrderIssueHandlingStatus.PROCESSING), feedbackRepository.countByHandlingStatus(FeedbackHandlingStatus.PENDING), feedbackRepository.countByHandlingStatus(FeedbackHandlingStatus.PROCESSING));
    }
}
