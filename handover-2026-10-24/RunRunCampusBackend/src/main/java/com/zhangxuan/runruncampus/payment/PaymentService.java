package com.zhangxuan.runruncampus.payment;

import com.zhangxuan.runruncampus.membership.MembershipPlanType;
import com.zhangxuan.runruncampus.membership.MembershipService;
import com.zhangxuan.runruncampus.order.CampusOrder;
import com.zhangxuan.runruncampus.order.OrderPaymentStatus;
import com.zhangxuan.runruncampus.order.OrderPaymentMethod;
import com.zhangxuan.runruncampus.order.OrderRepository;
import com.zhangxuan.runruncampus.order.OrderStatus;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class PaymentService {
    private static final Duration PAYMENT_WINDOW = Duration.ofMinutes(30);

    private final WechatPaymentRepository paymentRepository;
    private final WechatRefundRepository refundRepository;
    private final OrderRepository orderRepository;
    private final MembershipService membershipService;
    private final WechatPayGateway gateway;
    private final WechatPayProperties properties;

    public PaymentService(WechatPaymentRepository paymentRepository, WechatRefundRepository refundRepository,
                          OrderRepository orderRepository, MembershipService membershipService,
                          WechatPayGateway gateway, WechatPayProperties properties) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.orderRepository = orderRepository;
        this.membershipService = membershipService;
        this.gateway = gateway;
        this.properties = properties;
    }

    @Transactional
    public PaymentResponse createOrderPayment(AppUser customer, Long orderId) {
        requireCustomer(customer);
        CampusOrder order = orderRepository.findLockedById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能支付自己的订单");
        }
        if (order.getStatus() != OrderStatus.PENDING_ASSIGNMENT && order.getStatus() != OrderStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单当前状态不能支付");
        }
        if (order.getPaymentMethod() == OrderPaymentMethod.CASH_ON_DELIVERY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该订单为货到付款，无需发起线上支付");
        }
        if (order.getPaymentMethod() != OrderPaymentMethod.WECHAT_APP) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该订单无需发起微信 APP 支付");
        }
        int dueCents = order.getFeeCents() - order.getPaidCents();
        if (dueCents <= 0) return paidResponse(order);

        WechatPayment pending = paymentRepository
                .findFirstByOrderAndStatusOrderByCreatedAtDesc(order, PaymentStatus.PENDING).orElse(null);
        if (pending != null && !pending.isExpired(Instant.now()) && pending.getAmountCents() == dueCents) {
            return response(pending);
        }
        if (pending != null) close(pending);

        Instant expiresAt = Instant.now().plus(PAYMENT_WINDOW);
        WechatPayment payment = paymentRepository.save(WechatPayment.forOrder(customer, order, dueCents, expiresAt));
        WechatPayGateway.AppPayParameters parameters = gateway.prepay(payment.getOutTradeNo(),
                "跑跑代取服务-" + order.getOrderNo(), dueCents, "ORDER:" + order.getId(), expiresAt);
        payment.recordPrepay(parameters);
        return response(payment);
    }

    @Transactional
    public PaymentResponse createMembershipPayment(AppUser customer, MembershipPlanType plan) {
        requireCustomer(customer);
        membershipService.validateCanPurchase(customer);
        WechatPayment pending = paymentRepository.findFirstByUserAndBusinessTypeAndStatusOrderByCreatedAtDesc(
                customer, PaymentBusinessType.MEMBERSHIP, PaymentStatus.PENDING).orElse(null);
        if (pending != null && !pending.isExpired(Instant.now())) {
            if (pending.getMembershipPlan() != plan) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "已有待支付月卡，请先完成或等待其过期");
            }
            return response(pending);
        }
        if (pending != null) close(pending);

        Instant expiresAt = Instant.now().plus(PAYMENT_WINDOW);
        WechatPayment payment = paymentRepository.save(WechatPayment.forMembership(customer, plan, expiresAt));
        WechatPayGateway.AppPayParameters parameters = gateway.prepay(payment.getOutTradeNo(),
                "跑跑代取-" + plan.getTitle(), plan.getPriceCents(), "MEMBERSHIP:" + plan.name(), expiresAt);
        payment.recordPrepay(parameters);
        return response(payment);
    }

    @Transactional
    public PaymentResponse status(AppUser customer, String outTradeNo) {
        requireCustomer(customer);
        WechatPayment payment = paymentRepository.findLockedByOutTradeNo(outTradeNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "支付单不存在"));
        if (!payment.belongsTo(customer)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看此支付单");
        }
        if (payment.getStatus() == PaymentStatus.PENDING && payment.hasPayParameters()) {
            WechatPayGateway.TradeResult result = gateway.query(outTradeNo);
            if (result.paid()) confirmPaid(payment, result);
        }
        return response(payment);
    }

    @Transactional
    public void acceptPaymentNotification(WechatPayGateway.NotificationHeaders headers, String body) {
        WechatPayGateway.TradeResult result = gateway.parsePaymentNotification(headers, body);
        if (!result.paid()) return;
        WechatPayment payment = paymentRepository.findLockedByOutTradeNo(result.outTradeNo())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "支付单不存在"));
        confirmPaid(payment, result);
    }

    @Transactional
    public void acceptRefundNotification(WechatPayGateway.NotificationHeaders headers, String body) {
        WechatPayGateway.RefundNotificationResult result = gateway.parseRefundNotification(headers, body);
        WechatRefund refund = refundRepository.findLockedByOutRefundNo(result.outRefundNo())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "退款单不存在"));
        if (!refund.getPayment().getOutTradeNo().equals(result.outTradeNo())
                || refund.getAmountCents() != result.refundCents()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退款通知与本地退款单不一致");
        }
        refund.record(new WechatPayGateway.RefundResult(result.refundId(), result.status()));
        if (result.status() == RefundStatus.SUCCESS) finishOrderRefund(refund.getPayment().getOrder());
    }

    @Transactional
    public void closeAndRefundForCancellation(CampusOrder order) {
        if (order.getPaymentMethod() == OrderPaymentMethod.PERSONAL_QR_MANUAL) {
            if (order.getPaidCents() > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "已人工确认到账，请联系管理员线下退款");
            }
            return;
        }
        paymentRepository.findByOrderAndStatusOrderByCreatedAtAsc(order, PaymentStatus.PENDING).forEach(this::close);
        if (order.getPaidCents() > 0) scheduleRefund(order, order.getPaidCents(), "订单取消", true);
    }

    @Transactional
    public void refundPriceDifference(CampusOrder order, int refundCents) {
        if (order.getPaymentMethod() == OrderPaymentMethod.PERSONAL_QR_MANUAL) {
            if (refundCents > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "人工扫码付款差额请由管理员线下处理");
            }
            return;
        }
        if (refundCents > 0) scheduleRefund(order, refundCents, "包裹规格降档退差价", false);
    }

    @Transactional(readOnly = true)
    public List<RefundResponse> refundsForOrder(CampusOrder order) {
        return refundRepository.findByOrder(order).stream()
                .map(refund -> new RefundResponse(refund.getAmountCents(), refund.getReason(), refund.getStatus(),
                        refund.getCreatedAt(), refund.getUpdatedAt()))
                .toList();
    }

    private void confirmPaid(WechatPayment payment, WechatPayGateway.TradeResult result) {
        if (payment.getStatus() == PaymentStatus.PAID) return;
        if (!payment.getOutTradeNo().equals(result.outTradeNo())
                || !properties.appId().equals(result.appId())
                || !properties.merchantId().equals(result.merchantId())
                || payment.getAmountCents() != result.amountCents()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "支付通知与本地支付单不一致");
        }
        payment.markPaid(result.transactionId());
        if (payment.getBusinessType() == PaymentBusinessType.MEMBERSHIP) {
            membershipService.activatePaidPurchase(payment.getUser(), payment.getMembershipPlan());
            return;
        }
        CampusOrder order = orderRepository.findLockedById(payment.getOrder().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        order.recordPayment(payment.getAmountCents());
        if (order.getStatus() == OrderStatus.CANCELLED) {
            scheduleRefund(order, payment.getAmountCents(), "已取消订单的迟到支付", true);
        }
    }

    private void finishOrderRefund(CampusOrder order) {
        if (order == null || order.getStatus() != OrderStatus.CANCELLED) return;
        long paid = paymentRepository.sumPaidForOrder(order);
        long refunded = refundRepository.sumSucceededForOrder(order);
        if (paid > 0 && refunded >= paid) order.markRefunded();
    }

    private void scheduleRefund(CampusOrder order, int refundCents, String reason, boolean cancellation) {
        int remaining = refundCents;
        List<WechatPayment> paidPayments = paymentRepository
                .findByOrderAndStatusOrderByCreatedAtAsc(order, PaymentStatus.PAID);
        for (WechatPayment payment : paidPayments) {
            int requested = refundRepository.findByPaymentOrderByCreatedAtAsc(payment).stream()
                    .mapToInt(WechatRefund::getAmountCents).sum();
            int amount = Math.min(remaining, payment.getAmountCents() - requested);
            if (amount > 0) {
                refundRepository.save(new WechatRefund(payment, amount, reason));
                remaining -= amount;
            }
            if (remaining == 0) break;
        }
        if (remaining != 0) throw new IllegalStateException("订单可退款金额不足");
        order.recordRefundScheduled(refundCents, cancellation);
    }

    private void close(WechatPayment payment) {
        if (payment.hasPayParameters()) gateway.close(payment.getOutTradeNo());
        payment.close();
    }

    private PaymentResponse paidResponse(CampusOrder order) {
        return new PaymentResponse(null, PaymentBusinessType.ORDER, PaymentStatus.PAID, 0,
                order.getPaymentStatus(), null, null);
    }

    private PaymentResponse response(WechatPayment payment) {
        AppPaymentParameters parameters = payment.getStatus() == PaymentStatus.PENDING && payment.hasPayParameters()
                ? new AppPaymentParameters(payment.getAppId(), payment.getPartnerId(), payment.getPrepayId(),
                payment.getPackageValue(), payment.getNonceStr(), payment.getPayTimestamp(), payment.getPaySignature())
                : null;
        OrderPaymentStatus orderPaymentStatus = payment.getOrder() == null ? null : payment.getOrder().getPaymentStatus();
        return new PaymentResponse(payment.getOutTradeNo(), payment.getBusinessType(), payment.getStatus(),
                payment.getAmountCents(), orderPaymentStatus, payment.getExpiresAt(), parameters);
    }

    private void requireCustomer(AppUser user) {
        if (user.getRole() != UserRole.CUSTOMER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有用户账号可以发起支付");
        }
    }

    public record AppPaymentParameters(String appId, String partnerId, String prepayId, String packageValue,
                                       String nonceStr, String timestamp, String sign) { }
    public record PaymentResponse(String outTradeNo, PaymentBusinessType businessType, PaymentStatus status,
                                  int amountCents, OrderPaymentStatus orderPaymentStatus, Instant expiresAt,
                                  AppPaymentParameters payParameters) { }
    public record RefundResponse(int amountCents, String reason, RefundStatus status,
                                 Instant createdAt, Instant updatedAt) { }
}
