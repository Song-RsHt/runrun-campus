package com.zhangxuan.runruncampus.service.admin;
import com.zhangxuan.runruncampus.controller.admin.AdminDashboardController;

import com.zhangxuan.runruncampus.repository.courier.CourierCertificationRepository;
import com.zhangxuan.runruncampus.enums.courier.CourierCertificationStatus;
import com.zhangxuan.runruncampus.enums.order.OrderIssueHandlingStatus;
import com.zhangxuan.runruncampus.repository.order.OrderIssueRepository;
import com.zhangxuan.runruncampus.repository.order.OrderRepository;
import com.zhangxuan.runruncampus.enums.order.OrderStatus;
import com.zhangxuan.runruncampus.feedback.FeedbackHandlingStatus;
import com.zhangxuan.runruncampus.feedback.FeedbackRepository;
import com.zhangxuan.runruncampus.user.UserRepository;
import com.zhangxuan.runruncampus.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminDashboardService {
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final CourierCertificationRepository certificationRepository;
    private final OrderIssueRepository issueRepository;
    private final FeedbackRepository feedbackRepository;

    public AdminDashboardService(UserRepository userRepository, OrderRepository orderRepository,
                                 CourierCertificationRepository certificationRepository,
                                 OrderIssueRepository issueRepository, FeedbackRepository feedbackRepository) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.certificationRepository = certificationRepository;
        this.issueRepository = issueRepository;
        this.feedbackRepository = feedbackRepository;
    }

    @Transactional(readOnly = true)
    public AdminDashboardController.AdminDashboardResponse overview() {
        return new AdminDashboardController.AdminDashboardResponse(
                userRepository.count(),
                userRepository.countByRole(UserRole.COURIER),
                orderRepository.count(),
                orderRepository.countFulfillablePendingOrders(),
                orderRepository.countByStatus(OrderStatus.ACCEPTED)
                        + orderRepository.countByStatus(OrderStatus.PICKED_UP),
                certificationRepository.countByStatus(CourierCertificationStatus.PENDING),
                issueRepository.countByHandlingStatus(OrderIssueHandlingStatus.PENDING),
                issueRepository.countByHandlingStatus(OrderIssueHandlingStatus.PROCESSING),
                feedbackRepository.countByHandlingStatus(FeedbackHandlingStatus.PENDING),
                feedbackRepository.countByHandlingStatus(FeedbackHandlingStatus.PROCESSING));
    }
}
