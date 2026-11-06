package com.zhangxuan.runruncampus.service.impl;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.CampusOrder;
import com.zhangxuan.runruncampus.entity.OrderEvent;
import com.zhangxuan.runruncampus.entity.OrderIssue;
import com.zhangxuan.runruncampus.enums.OrderIssueType;
import com.zhangxuan.runruncampus.enums.OrderMessageType;
import com.zhangxuan.runruncampus.enums.OrderProofType;
import com.zhangxuan.runruncampus.enums.OrderStatus;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.exception.BusinessFailure;
import com.zhangxuan.runruncampus.repository.OrderEventRepository;
import com.zhangxuan.runruncampus.repository.OrderIssueRepository;
import com.zhangxuan.runruncampus.repository.OrderProofRepository;
import com.zhangxuan.runruncampus.repository.OrderRepository;
import com.zhangxuan.runruncampus.service.CourierReassignmentService;
import com.zhangxuan.runruncampus.service.OrderMessageService;
import com.zhangxuan.runruncampus.vo.TaskReleaseVO;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 骑手无法履约上报业务实现；通过注入的业务契约及数据访问组件执行，事务边界保留在本类。 */
@Service
public class CourierReassignmentServiceImpl implements CourierReassignmentService {

    private final OrderRepository orders;
    private final OrderProofRepository proofs;
    private final OrderIssueRepository issues;
    private final OrderEventRepository events;
    private final OrderMessageService messages;

    public CourierReassignmentServiceImpl(
            OrderRepository orders,
            OrderProofRepository proofs,
            OrderIssueRepository issues,
            OrderEventRepository events,
            OrderMessageService messages) {
        this.orders = orders;
        this.proofs = proofs;
        this.issues = issues;
        this.events = events;
        this.messages = messages;
    }

    @Transactional
    @Override
    public TaskReleaseVO reportUnable(AppUser courier, Long orderId, String reason) {
        String explanation = reason == null ? "" : reason.trim();
        if (explanation.isEmpty() || explanation.length() > 300) {
            throw new BusinessFailure(400, "请输入不超过300字的无法履约原因");
        }
        CampusOrder order =
                orders.findLockedById(orderId).orElseThrow(() -> new BusinessFailure(404, "订单不存在"));
        if (courier.getRole() != UserRole.COURIER
                || order.getCourier() == null
                || !order.getCourier().getId().equals(courier.getId())) {
            throw new BusinessFailure(403, "仅当前接单骑手可申请改派");
        }
        if (order.getStatus() != OrderStatus.ACCEPTED
                || proofs.existsByOrderAndType(order, OrderProofType.PICKUP)
                || proofs.existsByOrderAndType(order, OrderProofType.SIZE_REVIEW)) {
            throw new BusinessFailure(409, "已有取件凭证或订单状态已变化，请联系运营处理");
        }
        issues.save(
                new OrderIssue(order, courier, OrderIssueType.OTHER, "骑手无法履约（待改派）：" + explanation));
        order.requestReassignment();
        events.save(new OrderEvent(order, courier, OrderStatus.PENDING_REASSIGNMENT));
        messages.send(
                order.getCustomer(),
                order,
                OrderMessageType.ORDER_ISSUE,
                "骑手无法履约，等待运营改派",
                "运营正在安排新的骑手，请留意订单进度");
        return new TaskReleaseVO(order.getId(), order.getOrderNo(), order.getStatus());
    }
}
