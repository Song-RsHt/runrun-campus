package com.zhangxuan.runruncampus.order;

import com.zhangxuan.runruncampus.message.OrderMessageService;
import com.zhangxuan.runruncampus.message.OrderMessageType;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CourierReassignmentService {
    private final OrderRepository orders;
    private final OrderProofRepository proofs;
    private final OrderIssueRepository issues;
    private final OrderEventRepository events;
    private final OrderMessageService messages;

    public CourierReassignmentService(OrderRepository orders, OrderProofRepository proofs,
                                      OrderIssueRepository issues, OrderEventRepository events,
                                      OrderMessageService messages) {
        this.orders = orders;
        this.proofs = proofs;
        this.issues = issues;
        this.events = events;
        this.messages = messages;
    }

    @Transactional
    public ReleaseResponse reportUnable(AppUser courier, Long orderId, String reason) {
        String explanation = reason == null ? "" : reason.trim();
        if (explanation.isEmpty() || explanation.length() > 300) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入不超过300字的无法履约原因");
        }
        CampusOrder order = orders.findLockedById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (courier.getRole() != UserRole.COURIER || order.getCourier() == null
                || !order.getCourier().getId().equals(courier.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅当前接单骑手可申请改派");
        }
        if (order.getStatus() != OrderStatus.ACCEPTED
                || proofs.existsByOrderAndType(order, OrderProofType.PICKUP)
                || proofs.existsByOrderAndType(order, OrderProofType.SIZE_REVIEW)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已有取件凭证或订单状态已变化，请联系运营处理");
        }
        issues.save(new OrderIssue(order, courier, OrderIssueType.OTHER, "骑手无法履约（待改派）：" + explanation));
        order.requestReassignment();
        events.save(new OrderEvent(order, courier, OrderStatus.PENDING_REASSIGNMENT));
        messages.send(order.getCustomer(), order, OrderMessageType.ORDER_ISSUE,
                "骑手无法履约，等待运营改派", "运营正在安排新的骑手，请留意订单进度");
        return new ReleaseResponse(order.getId(), order.getOrderNo(), order.getStatus());
    }

    public record ReleaseResponse(Long orderId, String orderNo, OrderStatus status) { }
}
