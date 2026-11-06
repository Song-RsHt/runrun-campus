package com.zhangxuan.runruncampus.service.impl;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.CampusOrder;
import com.zhangxuan.runruncampus.entity.OrderIssue;
import com.zhangxuan.runruncampus.enums.OrderIssueHandlingStatus;
import com.zhangxuan.runruncampus.enums.OrderIssueType;
import com.zhangxuan.runruncampus.enums.OrderMessageType;
import com.zhangxuan.runruncampus.exception.BusinessFailure;
import com.zhangxuan.runruncampus.repository.OrderIssueRepository;
import com.zhangxuan.runruncampus.repository.OrderRepository;
import com.zhangxuan.runruncampus.service.OrderIssueService;
import com.zhangxuan.runruncampus.service.OrderMessageService;
import com.zhangxuan.runruncampus.vo.OrderIssueVO;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;

/** 订单异常上报业务实现；通过注入的业务契约及数据访问组件执行，事务边界保留在本类。 */
@Service
public class OrderIssueServiceImpl implements OrderIssueService {

    private final OrderRepository orderRepository;
    private final OrderIssueRepository issueRepository;
    private final OrderMessageService messageService;

    public OrderIssueServiceImpl(
            OrderRepository orderRepository,
            OrderIssueRepository issueRepository,
            OrderMessageService messageService) {
        this.orderRepository = orderRepository;
        this.issueRepository = issueRepository;
        this.messageService = messageService;
    }

    @Transactional
    @Override
    public OrderIssueVO create(
            AppUser reporter, Long orderId, OrderIssueType type, String description) {
        CampusOrder order =
                orderRepository
                        .findLockedById(orderId)
                        .orElseThrow(() -> new BusinessFailure(404, "订单不存在"));
        requireParticipant(reporter, order);
        String normalized = description == null ? "" : description.trim();
        if (type == null || normalized.isEmpty() || normalized.length() > 500) {
            throw new BusinessFailure(400, "请填写不超过500字的异常说明");
        }
        if (issueRepository.existsByOrderAndReporterAndTypeAndHandlingStatusIn(
                order,
                reporter,
                type,
                EnumSet.of(
                        OrderIssueHandlingStatus.PENDING, OrderIssueHandlingStatus.PROCESSING))) {
            throw new BusinessFailure(409, "该异常已有待处理工单，请勿重复提交");
        }
        OrderIssue issue = issueRepository.save(new OrderIssue(order, reporter, type, normalized));
        AppUser recipient =
                reporter.getId().equals(order.getCustomer().getId())
                        ? order.getCourier()
                        : order.getCustomer();
        if (recipient != null) {
            messageService.send(
                    recipient,
                    order,
                    OrderMessageType.ORDER_ISSUE,
                    "订单收到异常反馈",
                    reporter.getDisplayName() + " 提交了异常反馈：" + normalized);
        }
        return response(issue);
    }

    @Transactional(readOnly = true)
    @Override
    public List<OrderIssueVO> list(AppUser user, Long orderId) {
        CampusOrder order = requireParticipant(user, orderId);
        return issueRepository.findByOrderOrderByCreatedAtAsc(order).stream()
                .map(this::response)
                .toList();
    }

    private CampusOrder requireParticipant(AppUser user, Long orderId) {
        CampusOrder order =
                orderRepository
                        .findById(orderId)
                        .orElseThrow(() -> new BusinessFailure(404, "订单不存在"));
        requireParticipant(user, order);
        return order;
    }

    private void requireParticipant(AppUser user, CampusOrder order) {
        boolean isCustomer = user.getId().equals(order.getCustomer().getId());
        boolean isCourier =
                order.getCourier() != null && user.getId().equals(order.getCourier().getId());
        if (!isCustomer && !isCourier) {
            throw new BusinessFailure(403, "无权查看或反馈此订单");
        }
    }

    private OrderIssueVO response(OrderIssue issue) {
        return new OrderIssueVO(
                issue.getId(),
                issue.getType(),
                issue.getDescription(),
                issue.getReporter().getDisplayName(),
                issue.getReporter().getRole(),
                issue.getCreatedAt(),
                issue.getHandlingStatus(),
                issue.getResolution(),
                issue.getHandledAt(),
                issue.getUpdatedAt());
    }
}
