package com.zhangxuan.runruncampus.service.impl.admin;

import com.zhangxuan.runruncampus.entity.order.CampusOrder;
import com.zhangxuan.runruncampus.entity.order.OrderEvent;
import com.zhangxuan.runruncampus.entity.user.AppUser;
import com.zhangxuan.runruncampus.enums.admin.AdminAuditAction;
import com.zhangxuan.runruncampus.enums.admin.AdminAuditTargetType;
import com.zhangxuan.runruncampus.enums.message.OrderMessageType;
import com.zhangxuan.runruncampus.enums.order.CourierSettlementStatus;
import com.zhangxuan.runruncampus.enums.order.ManualPaymentStatus;
import com.zhangxuan.runruncampus.enums.order.OrderPaymentMethod;
import com.zhangxuan.runruncampus.enums.order.OrderPaymentStatus;
import com.zhangxuan.runruncampus.enums.order.OrderProofType;
import com.zhangxuan.runruncampus.enums.order.OrderStatus;
import com.zhangxuan.runruncampus.enums.user.UserRole;
import com.zhangxuan.runruncampus.exception.BusinessFailure;
import com.zhangxuan.runruncampus.repository.order.OrderEventRepository;
import com.zhangxuan.runruncampus.repository.order.OrderProofRepository;
import com.zhangxuan.runruncampus.repository.order.OrderRepository;
import com.zhangxuan.runruncampus.service.admin.AdminAuditService;
import com.zhangxuan.runruncampus.service.admin.AdminOrderLifecycleService;
import com.zhangxuan.runruncampus.service.message.OrderMessageService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理员订单收尾与归档业务实现；通过注入的业务契约及数据访问组件执行，事务边界保留在本类。 */
@Service
public class AdminOrderLifecycleServiceImpl implements AdminOrderLifecycleService {

    private final OrderRepository orders;
    private final OrderProofRepository proofs;
    private final OrderEventRepository events;
    private final OrderMessageService messages;
    private final AdminAuditService audit;

    public AdminOrderLifecycleServiceImpl(
            OrderRepository orders,
            OrderProofRepository proofs,
            OrderEventRepository events,
            OrderMessageService messages,
            AdminAuditService audit) {
        this.orders = orders;
        this.proofs = proofs;
        this.events = events;
        this.messages = messages;
        this.audit = audit;
    }

    @Transactional
    @Override
    public void forceComplete(AppUser administrator, Long id, String reason) {
        String note = requireReason(reason);
        CampusOrder order = findLocked(administrator, id);
        if (order.getArchivedAt() != null
                || order.getStatus() != OrderStatus.PICKED_UP
                || order.getCourier() == null) {
            throw new BusinessFailure(409, "仅未归档且已取件的订单可人工完成");
        }
        if (!proofs.existsByOrderAndType(order, OrderProofType.DELIVERY)) {
            throw new BusinessFailure(409, "缺少送达凭证，不能人工完成");
        }
        if (order.getPaymentStatus() != OrderPaymentStatus.PAID
                || order.getPaidCents() < order.getFeeCents()
                || (order.getPaymentMethod() == OrderPaymentMethod.PERSONAL_QR_MANUAL
                        && order.getFeeCents() > 0
                        && order.getManualPaymentStatus() != ManualPaymentStatus.CONFIRMED)) {
            throw new BusinessFailure(409, "收款尚未确认，不能人工完成");
        }
        order.updateStatus(OrderStatus.COMPLETED);
        order.markCourierSettlementPending();
        events.save(new OrderEvent(order, administrator, OrderStatus.COMPLETED));
        messages.send(
                order.getCustomer(),
                order,
                OrderMessageType.ORDER_COMPLETED,
                "订单已由管理员确认完成",
                "管理员核对履约凭证后将订单标记为已完成；如有异议，请联系平台客服。");
        messages.send(
                order.getCourier(),
                order,
                OrderMessageType.ORDER_COMPLETED,
                "订单已由管理员确认完成",
                "订单 " + order.getOrderNo() + " 已由管理员核对后标记为已完成。");
        audit.record(
                administrator,
                AdminAuditAction.ORDER_FORCE_COMPLETED,
                AdminAuditTargetType.ORDER,
                order.getId(),
                "人工完成订单 %s；原因：%s".formatted(order.getOrderNo(), note));
    }

    @Transactional
    @Override
    public void archive(AppUser administrator, Long id, String reason) {
        String note = requireReason(reason);
        CampusOrder order = findLocked(administrator, id);
        if (order.getArchivedAt() != null
                || (order.getStatus() != OrderStatus.COMPLETED
                        && order.getStatus() != OrderStatus.CANCELLED)) {
            throw new BusinessFailure(409, "仅未归档的已结束订单可归档");
        }
        if (order.getPaymentStatus() == OrderPaymentStatus.REFUNDING
                || order.getCourierSettlementStatus() == CourierSettlementStatus.PENDING
                || (order.getStatus() == OrderStatus.COMPLETED
                        && order.getPaymentStatus() != OrderPaymentStatus.PAID)) {
            throw new BusinessFailure(409, "订单仍有待处理的收款、退款或骑手结算");
        }
        order.archive(administrator);
        audit.record(
                administrator,
                AdminAuditAction.ORDER_ARCHIVED,
                AdminAuditTargetType.ORDER,
                order.getId(),
                "归档订单 %s；原因：%s".formatted(order.getOrderNo(), note));
    }

    @Transactional
    @Override
    public void unarchive(AppUser administrator, Long id) {
        CampusOrder order = findLocked(administrator, id);
        if (order.getArchivedAt() == null) {
            throw new BusinessFailure(409, "订单未归档");
        }
        order.unarchive();
        audit.record(
                administrator,
                AdminAuditAction.ORDER_UNARCHIVED,
                AdminAuditTargetType.ORDER,
                order.getId(),
                "恢复归档订单 " + order.getOrderNo());
    }

    private CampusOrder findLocked(AppUser administrator, Long id) {
        if (administrator == null || administrator.getRole() != UserRole.ADMIN) {
            throw new BusinessFailure(403, "仅管理员可操作");
        }
        return orders.findLockedById(id).orElseThrow(() -> new BusinessFailure(404, "订单不存在"));
    }

    private String requireReason(String reason) {
        String note = reason == null ? "" : reason.trim();
        if (note.length() < 5 || note.length() > 200) {
            throw new BusinessFailure(400, "请输入5至200字的操作原因");
        }
        return note;
    }
}
