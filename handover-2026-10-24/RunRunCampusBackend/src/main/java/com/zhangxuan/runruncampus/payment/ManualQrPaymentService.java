package com.zhangxuan.runruncampus.payment;

import com.zhangxuan.runruncampus.admin.AdminAuditAction;
import com.zhangxuan.runruncampus.admin.AdminAuditService;
import com.zhangxuan.runruncampus.admin.AdminAuditTargetType;
import com.zhangxuan.runruncampus.message.OrderMessageService;
import com.zhangxuan.runruncampus.message.OrderMessageType;
import com.zhangxuan.runruncampus.order.CampusOrder;
import com.zhangxuan.runruncampus.order.CourierSettlementStatus;
import com.zhangxuan.runruncampus.order.ManualPaymentStatus;
import com.zhangxuan.runruncampus.order.OrderPaymentMethod;
import com.zhangxuan.runruncampus.order.OrderRepository;
import com.zhangxuan.runruncampus.order.OrderStatus;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
public class ManualQrPaymentService {
    private final OrderRepository orderRepository;
    private final OrderMessageService messageService;
    private final AdminAuditService auditService;

    public ManualQrPaymentService(OrderRepository orderRepository, OrderMessageService messageService,
                                  AdminAuditService auditService) {
        this.orderRepository = orderRepository;
        this.messageService = messageService;
        this.auditService = auditService;
    }

    @Transactional
    public ManualPaymentResponse requestPayment(AppUser courier, Long orderId) {
        CampusOrder order = findLocked(orderId);
        requireCourier(courier, order);
        requireManualQr(order);
        if (order.getStatus() != OrderStatus.PICKED_UP) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "取件并到达交付地点后才能通知付款");
        }
        if (order.getPaymentStatus() == com.zhangxuan.runruncampus.order.OrderPaymentStatus.PAID) {
            return response(order);
        }
        if (order.getManualPaymentStatus() != ManualPaymentStatus.NOT_REQUESTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已经发起过付款通知");
        }
        order.requestManualPayment();
        messageService.send(order.getCustomer(), order, OrderMessageType.MANUAL_PAYMENT_REQUESTED,
                "骑手已到达，请付款", "请在订单内扫码支付 ¥%.2f，提交后等待管理员核款。".formatted(
                        order.getFeeCents() / 100.0));
        return response(order);
    }

    @Transactional
    public ManualPaymentResponse submitPayment(AppUser customer, Long orderId, String payerReference) {
        CampusOrder order = findLocked(orderId);
        requireCustomer(customer, order);
        requireManualQr(order);
        if (order.getStatus() != OrderStatus.PICKED_UP) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单当前状态不能提交付款");
        }
        if (order.getManualPaymentStatus() != ManualPaymentStatus.AWAITING_PAYMENT
                && order.getManualPaymentStatus() != ManualPaymentStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请等待骑手到达并发起付款通知");
        }
        String normalizedReference = payerReference == null ? "" : payerReference.trim();
        if (normalizedReference.isEmpty() || normalizedReference.length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入不超过64字的付款账号备注");
        }
        order.submitManualPayment(normalizedReference);
        messageService.send(order.getCourier(), order, OrderMessageType.MANUAL_PAYMENT_SUBMITTED,
                "用户已提交付款", "订单 " + order.getOrderNo() + " 正在等待管理员核对到账，请勿提前交付。");
        return response(order);
    }

    @Transactional
    public ManualPaymentResponse confirmPayment(AppUser administrator, Long orderId) {
        CampusOrder order = findLocked(orderId);
        requireAdministrator(administrator);
        requireManualQr(order);
        if (order.getManualPaymentStatus() != ManualPaymentStatus.SUBMITTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该订单没有待核对的付款申报");
        }
        order.confirmManualPayment(administrator);
        messageService.send(order.getCustomer(), order, OrderMessageType.MANUAL_PAYMENT_CONFIRMED,
                "付款已确认", "管理员已确认到账，可以在收到物品后向骑手提供收货码。");
        messageService.send(order.getCourier(), order, OrderMessageType.MANUAL_PAYMENT_CONFIRMED,
                "平台已确认收款", "订单 " + order.getOrderNo() + " 已确认到账，请完成交付并验证收货码。");
        auditService.record(administrator, AdminAuditAction.MANUAL_PAYMENT_CONFIRMED,
                AdminAuditTargetType.ORDER, order.getId(), "确认订单 %s 人工扫码付款到账 ¥%.2f".formatted(
                        order.getOrderNo(), order.getFeeCents() / 100.0));
        return response(order);
    }

    @Transactional
    public ManualPaymentResponse rejectPayment(AppUser administrator, Long orderId, String reason) {
        CampusOrder order = findLocked(orderId);
        requireAdministrator(administrator);
        requireManualQr(order);
        if (order.getManualPaymentStatus() != ManualPaymentStatus.SUBMITTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该订单没有待核对的付款申报");
        }
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isEmpty() || normalizedReason.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入不超过200字的驳回原因");
        }
        order.rejectManualPayment(administrator, normalizedReason);
        messageService.send(order.getCustomer(), order, OrderMessageType.MANUAL_PAYMENT_REJECTED,
                "付款核对未通过", normalizedReason + "。请核对后重新提交。");
        messageService.send(order.getCourier(), order, OrderMessageType.MANUAL_PAYMENT_REJECTED,
                "付款核对未通过", "请等待用户重新提交付款信息，不要提前交付。");
        auditService.record(administrator, AdminAuditAction.MANUAL_PAYMENT_REJECTED,
                AdminAuditTargetType.ORDER, order.getId(), "驳回订单 %s 人工扫码付款：%s".formatted(
                        order.getOrderNo(), normalizedReason));
        return response(order);
    }

    @Transactional
    public ManualPaymentResponse confirmCourierSettlement(AppUser administrator, Long orderId) {
        CampusOrder order = findLocked(orderId);
        requireAdministrator(administrator);
        requireManualQr(order);
        if (order.getStatus() != OrderStatus.COMPLETED
                || order.getCourierSettlementStatus() != CourierSettlementStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "仅已完成且待结算的订单可以登记结算");
        }
        order.confirmCourierSettlement(administrator);
        messageService.send(order.getCourier(), order, OrderMessageType.COURIER_SETTLEMENT_CONFIRMED,
                "本单已登记结算", "订单 " + order.getOrderNo() + " 已由管理员登记为已结算。");
        auditService.record(administrator, AdminAuditAction.COURIER_SETTLEMENT_CONFIRMED,
                AdminAuditTargetType.ORDER, order.getId(), "登记订单 %s 已向骑手结算 ¥%.2f".formatted(
                        order.getOrderNo(), order.getFeeCents() / 100.0));
        return response(order);
    }

    @Transactional(readOnly = true)
    public ManualPaymentResponse status(AppUser participant, Long orderId) {
        CampusOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (!order.getCustomer().getId().equals(participant.getId())
                && (order.getCourier() == null || !order.getCourier().getId().equals(participant.getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看该订单付款状态");
        }
        requireManualQr(order);
        return response(order);
    }

    private CampusOrder findLocked(Long orderId) {
        return orderRepository.findLockedById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
    }

    private void requireCourier(AppUser courier, CampusOrder order) {
        if (courier.getRole() != UserRole.COURIER || order.getCourier() == null
                || !order.getCourier().getId().equals(courier.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能操作自己承接的订单");
        }
    }

    private void requireCustomer(AppUser customer, CampusOrder order) {
        if (customer.getRole() != UserRole.CUSTOMER || !order.getCustomer().getId().equals(customer.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能提交自己订单的付款信息");
        }
    }

    private void requireAdministrator(AppUser administrator) {
        if (administrator.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可以核对付款");
        }
    }

    private void requireManualQr(CampusOrder order) {
        if (order.getPaymentMethod() != OrderPaymentMethod.PERSONAL_QR_MANUAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该订单不是人工扫码付款");
        }
    }

    private ManualPaymentResponse response(CampusOrder order) {
        return new ManualPaymentResponse(order.getId(), order.getManualPaymentStatus(), order.getFeeCents(),
                order.getPayerReference(), order.getPaymentRequestedAt(), order.getPaymentSubmittedAt(),
                order.getPaymentReviewedAt(), name(order.getPaymentReviewer()), order.getPaymentReviewNote(),
                order.getCourierSettlementStatus(), order.getCourierSettledAt(),
                name(order.getCourierSettlementReviewer()));
    }

    private String name(AppUser user) {
        return user == null ? null : user.getDisplayName();
    }

    public record ManualPaymentResponse(Long orderId, ManualPaymentStatus status, int amountCents,
                                        String payerReference, Instant requestedAt, Instant submittedAt,
                                        Instant reviewedAt, String reviewerName, String reviewNote,
                                        CourierSettlementStatus settlementStatus, Instant settledAt,
                                        String settlementReviewerName) { }
}
