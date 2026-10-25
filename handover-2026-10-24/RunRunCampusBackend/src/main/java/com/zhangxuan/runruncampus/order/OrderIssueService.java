package com.zhangxuan.runruncampus.order;

import com.zhangxuan.runruncampus.message.OrderMessageService;
import com.zhangxuan.runruncampus.message.OrderMessageType;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

@Service
public class OrderIssueService {
    private final OrderRepository orderRepository;
    private final OrderIssueRepository issueRepository;
    private final OrderMessageService messageService;

    public OrderIssueService(OrderRepository orderRepository, OrderIssueRepository issueRepository,
                             OrderMessageService messageService) {
        this.orderRepository = orderRepository;
        this.issueRepository = issueRepository;
        this.messageService = messageService;
    }

    @Transactional
    public IssueResponse create(AppUser reporter, Long orderId, OrderIssueType type, String description) {
        CampusOrder order = orderRepository.findLockedById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        requireParticipant(reporter, order);
        String normalized = description == null ? "" : description.trim();
        if (type == null || normalized.isEmpty() || normalized.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写不超过500字的异常说明");
        }
        if (issueRepository.existsByOrderAndReporterAndTypeAndHandlingStatusIn(
                order, reporter, type,
                EnumSet.of(OrderIssueHandlingStatus.PENDING, OrderIssueHandlingStatus.PROCESSING))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该异常已有待处理工单，请勿重复提交");
        }
        OrderIssue issue = issueRepository.save(new OrderIssue(order, reporter, type, normalized));
        AppUser recipient = reporter.getId().equals(order.getCustomer().getId())
                ? order.getCourier() : order.getCustomer();
        if (recipient != null) {
            messageService.send(recipient, order, OrderMessageType.ORDER_ISSUE,
                    "订单收到异常反馈", reporter.getDisplayName() + " 提交了异常反馈：" + normalized);
        }
        return response(issue);
    }

    @Transactional(readOnly = true)
    public List<IssueResponse> list(AppUser user, Long orderId) {
        CampusOrder order = requireParticipant(user, orderId);
        return issueRepository.findByOrderOrderByCreatedAtAsc(order).stream().map(this::response).toList();
    }

    private CampusOrder requireParticipant(AppUser user, Long orderId) {
        CampusOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        requireParticipant(user, order);
        return order;
    }

    private void requireParticipant(AppUser user, CampusOrder order) {
        boolean isCustomer = user.getId().equals(order.getCustomer().getId());
        boolean isCourier = order.getCourier() != null && user.getId().equals(order.getCourier().getId());
        if (!isCustomer && !isCourier) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看或反馈此订单");
        }
    }

    private IssueResponse response(OrderIssue issue) {
        return new IssueResponse(issue.getId(), issue.getType(), issue.getDescription(),
                issue.getReporter().getDisplayName(), issue.getReporter().getRole(), issue.getCreatedAt(),
                issue.getHandlingStatus(), issue.getResolution(), issue.getHandledAt(), issue.getUpdatedAt());
    }

    public record IssueResponse(Long id, OrderIssueType type, String description,
                                String reporterName, UserRole reporterRole, Instant createdAt,
                                OrderIssueHandlingStatus handlingStatus, String resolution,
                                Instant handledAt, Instant updatedAt) { }
}
