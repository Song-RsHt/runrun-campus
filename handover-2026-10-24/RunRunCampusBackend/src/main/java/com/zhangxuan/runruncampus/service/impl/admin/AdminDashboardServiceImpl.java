package com.zhangxuan.runruncampus.service.impl.admin;

import com.zhangxuan.runruncampus.enums.courier.CourierCertificationStatus;
import com.zhangxuan.runruncampus.enums.feedback.FeedbackHandlingStatus;
import com.zhangxuan.runruncampus.enums.order.OrderIssueHandlingStatus;
import com.zhangxuan.runruncampus.enums.order.OrderStatus;
import com.zhangxuan.runruncampus.enums.user.UserRole;
import com.zhangxuan.runruncampus.repository.courier.CourierCertificationRepository;
import com.zhangxuan.runruncampus.repository.feedback.FeedbackRepository;
import com.zhangxuan.runruncampus.repository.order.OrderIssueRepository;
import com.zhangxuan.runruncampus.repository.order.OrderRepository;
import com.zhangxuan.runruncampus.repository.user.UserRepository;
import com.zhangxuan.runruncampus.service.admin.AdminDashboardService;
import com.zhangxuan.runruncampus.vo.admin.AdminDashboardResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 后台业务概览业务实现；通过注入的业务契约及数据访问组件执行，事务边界保留在本类。 */
@Service
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final CourierCertificationRepository certificationRepository;
    private final OrderIssueRepository issueRepository;
    private final FeedbackRepository feedbackRepository;

    public AdminDashboardServiceImpl(
            UserRepository userRepository,
            OrderRepository orderRepository,
            CourierCertificationRepository certificationRepository,
            OrderIssueRepository issueRepository,
            FeedbackRepository feedbackRepository) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.certificationRepository = certificationRepository;
        this.issueRepository = issueRepository;
        this.feedbackRepository = feedbackRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public AdminDashboardResponse overview() {
        return new AdminDashboardResponse(
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
