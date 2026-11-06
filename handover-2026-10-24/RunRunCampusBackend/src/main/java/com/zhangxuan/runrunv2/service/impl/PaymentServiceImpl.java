package com.zhangxuan.runrunv2.service.impl;

import com.zhangxuan.runrunv2.config.OrderJson;
import com.zhangxuan.runrunv2.dto.CancelRequest;
import com.zhangxuan.runrunv2.dto.PaymentResultRequest;
import com.zhangxuan.runrunv2.dto.PaymentVersionRequest;
import com.zhangxuan.runrunv2.enums.Result;
import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.integration.idempotency.OrderIdempotency;
import com.zhangxuan.runrunv2.integration.payment.MockPaymentAdapter;
import com.zhangxuan.runrunv2.mapper.OrderStore;
import com.zhangxuan.runrunv2.mapper.PaymentMapper;
import com.zhangxuan.runrunv2.model.InputValidation;
import com.zhangxuan.runrunv2.model.ResourceIds;
import com.zhangxuan.runrunv2.service.IdentityService;
import com.zhangxuan.runrunv2.service.PaymentService;
import com.zhangxuan.runrunv2.vo.AdjustmentFundsVO;
import com.zhangxuan.runrunv2.vo.CancelledOrderVO;
import com.zhangxuan.runrunv2.vo.OrderVO;
import com.zhangxuan.runrunv2.vo.OriginalPaymentVO;
import com.zhangxuan.runrunv2.vo.PaymentAttemptVO;
import com.zhangxuan.runrunv2.vo.PaymentOrderVO;
import com.zhangxuan.runrunv2.vo.PaymentSummaryVO;
import com.zhangxuan.runrunv2.vo.RefundOrderVO;
import com.zhangxuan.runrunv2.vo.StartedPaymentVO;
import java.math.BigInteger;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;














































































/**
 * 编排隔离支付、退款和资金事件。行锁、渠道事件幂等与并发版本共同防止重复推进；金额继续使用整数分。 渠道交互通过适配器完成，Mapper 不承担状态机。参见《支付与退款规则》。
 */
@Service
@ConditionalOnProperty(name = "rrc.payment.mock-enabled", havingValue = "true")
public class PaymentServiceImpl implements PaymentService {

    private final PaymentMapper mapper;

    private final IdentityService identity;

    private final OrderStore orders;

    private final OrderIdempotency requests;

    private final OrderJson json;

    private final MockPaymentAdapter channel;

    private final Clock clock;

    public PaymentServiceImpl(PaymentMapper mapper, IdentityService identity, OrderStore orders, OrderIdempotency requests, OrderJson json, MockPaymentAdapter channel, Clock clock) {
        this.mapper = mapper;
        this.identity = identity;
        this.orders = orders;
        this.requests = requests;
        this.json = json;
        this.channel = channel;
        this.clock = clock;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public OriginalPaymentVO create(String orderId, long amountCents, OffsetDateTime deadline) {
        var order = orders.read(orderId, true);
        if (!order.status().equals("PENDING_PAYMENT") || amountCents != order.originalFeeCents() || !deadline.toInstant().equals(order.pendingPaymentDeadline().toInstant()))
            throw conflict();
        String id = mapper.insertOriginalPayment(Long.valueOf(orderId), amountCents, deadline);
        return new OriginalPaymentVO(id, orderId, "ORIGINAL", amountCents, "UNPAID", deadline, 0);
    }

    @Transactional(readOnly = true)
    @Override
    public PaymentSummaryVO get(String token, String paymentId) {
        var actor = identity.requireRole(token, "USER", false);
        PaymentOrderVO payment = payment(paymentId, false);
        own(actor.id(), orders.read(payment.orderId(), false));
        return summary(payment);
    }

    @Transactional
    @Override
    public StartedPaymentVO start(String token, String paymentId, String key, PaymentVersionRequest body) {
        var actor = identity.authorizeForWrite(token, "USER", false);
        PaymentOrderVO p = lockPayment(paymentId, actor.id());
        var content = List.of(paymentId, required(body));
        StartedPaymentVO replay = requests.replay(actor.id(), "payments:attempt", key, content, StartedPaymentVO.class);
        if (replay != null)
            return replay;
        version(p.version(), body.expectedVersion());
        if (p.status().equals("CLOSED") || originalExpired(p))
            throw new IdentityFailure(409, "PAYMENT_CLOSED");
        if (p.status().equals("PAID"))
            throw conflict();
        // UNKNOWN includes transport timeout: only reconciliation can clear the old attempt.
        if (!active(p.id()).isEmpty())
            throw new IdentityFailure(409, "PAYMENT_RESULT_PENDING");
        int number = mapper.findNextAttemptNumber(Long.valueOf(p.id()));
        String id = mapper.insertPaymentAttempt(Long.valueOf(p.id()), number, "mock-payment-" + UUID.randomUUID());
        channel.submit("PAYMENT", id);
        paymentStatus(p.id(), "PROCESSING");
        StartedPaymentVO response = new StartedPaymentVO(payment(p.id(), false), attempt(id));
        event(p.orderId(), actor.id(), "PAYMENT_ATTEMPT_STARTED", id, Map.of("paymentId", p.id(), "attemptId", id));
        requests.finish(actor.id(), "payments:attempt", key, "PAYMENT_ATTEMPT", id, response);
        return response;
    }

    @Transactional
    @Override
    public PaymentSummaryVO queryPayment(String token, String id, String key, PaymentVersionRequest body) {
        var actor = identity.authorizeForWrite(token, "USER", false);
        PaymentOrderVO p = lockPayment(id, actor.id());
        PaymentSummaryVO replay = requests.replay(actor.id(), "payments:query", key, List.of(id, required(body)), PaymentSummaryVO.class);
        if (replay != null)
            return replay;
        version(p.version(), body.expectedVersion());
        // Query even after close: a lost late receipt must still be compensated.
        for (PaymentAttemptVO a : attempts(id)) if (!a.status().equals("SUCCESS"))
            applyPayment(p, a, channel.query("PAYMENT", a.id()), "query:payment:" + a.id() + ":" + channel.revision("PAYMENT", a.id()));
        PaymentSummaryVO result = summary(payment(id, false));
        requests.finish(actor.id(), "payments:query", key, "PAYMENT_ORDER", id, result);
        return result;
    }

    @Transactional
    @Override
    public PaymentAttemptVO paymentResult(String token, String id, String key, PaymentResultRequest body) {
        var actor = identity.authorizeForWrite(token, "ADMIN", false);
        PaymentAttemptVO a = attempt(id);
        PaymentOrderVO p = lockPayment(a.paymentOrderId(), null);
        a = attempt(id);
        validateResult(body);
        PaymentAttemptVO replay = requests.replay(actor.id(), "payments:result", key, List.of(id, body), PaymentAttemptVO.class);
        if (replay != null)
            return replay;
        if (!knownEvent("PAYMENT", id, body.result(), body.eventId())) {
            version(a.version(), body.expectedVersion());
            channel.record("PAYMENT", id, body.result());
            applyPayment(p, a, body.result(), body.eventId());
        }
        audit(actor.id(), "PAYMENT_RESULT", id, key, "isolated simulated channel result");
        PaymentAttemptVO result = attempt(id);
        requests.finish(actor.id(), "payments:result", key, "PAYMENT_ATTEMPT", id, result);
        return result;
    }

    @Transactional
    @Override
    public RefundOrderVO refundResult(String token, String id, String key, PaymentResultRequest body) {
        var actor = identity.authorizeForWrite(token, "ADMIN", false);
        RefundOrderVO r = refund(id);
        lockPayment(r.paymentOrderId(), null);
        r = refund(id);
        validateResult(body);
        RefundOrderVO replay = requests.replay(actor.id(), "refunds:result", key, List.of(id, body), RefundOrderVO.class);
        if (replay != null)
            return replay;
        if (!knownEvent("REFUND", id, body.result(), body.eventId())) {
            version(r.version(), body.expectedVersion());
            channel.record("REFUND", id, body.result());
            applyRefund(r, body.result(), body.eventId());
        }
        audit(actor.id(), "REFUND_RESULT", id, key, "isolated simulated channel result");
        RefundOrderVO result = refund(id);
        requests.finish(actor.id(), "refunds:result", key, "REFUND_ORDER", id, result);
        return result;
    }

    @Transactional
    @Override
    public RefundOrderVO queryRefund(String token, String id, String key, PaymentVersionRequest body) {
        var actor = identity.authorizeForWrite(token, "USER", false);
        RefundOrderVO r = refund(id);
        lockPayment(r.paymentOrderId(), actor.id());
        r = refund(id);
        RefundOrderVO replay = requests.replay(actor.id(), "refunds:query", key, List.of(id, required(body)), RefundOrderVO.class);
        if (replay != null)
            return replay;
        version(r.version(), body.expectedVersion());
        applyRefund(r, channel.query("REFUND", id), "query:refund:" + id + ":" + channel.revision("REFUND", id));
        RefundOrderVO result = refund(id);
        requests.finish(actor.id(), "refunds:query", key, "REFUND_ORDER", id, result);
        return result;
    }

    @Transactional
    @Override
    public CancelledOrderVO cancel(String token, String id, String key, CancelRequest body) {
        var actor = identity.authorizeForWrite(token, "USER", false);
        var order = orders.read(id, true);
        own(actor.id(), order);
        if (body == null)
            throw IdentityFailure.invalid();
        InputValidation.text(body.reason(), 500);
        String reason = body.reason();
        CancelledOrderVO replay = requests.replay(actor.id(), "payments:cancel", key, List.of(id, body), CancelledOrderVO.class);
        if (replay != null)
            return replay;
        version(order.version(), body.expectedVersion());
        if (!Set.of("PENDING_PAYMENT", "PENDING_ASSIGNMENT", "ACCEPTED").contains(order.status()))
            throw conflict();
        closeOrder(id);
        // Cancel all financial obligations, retaining their evidence and every receipt.
        mapper.cancelPendingAdjustments(now(), Long.valueOf(id));
        for (PaymentOrderVO p : paymentsForOrder(id)) {
            payment(p.id(), true);
            for (PaymentAttemptVO a : attempts(p.id())) if (a.status().equals("SUCCESS")) {
                long available = available(a.id(), p.amountCents());
                if (available > 0)
                    createRefund(p, a.id(), null, available, "CANCELLATION", "cancel:" + id + ":" + a.id(), reason);
            }
            if (!p.status().equals("PAID") && !p.status().equals("CLOSED"))
                paymentStatus(p.id(), "CLOSED");
        }
        event(id, actor.id(), "ORDER_CANCELLED", key, Map.of("reason", reason));
        notifyUser(id, "cancel:" + id, "ORDER_CANCELLED");
        CancelledOrderVO result = new CancelledOrderVO(orders.read(id, false), refundsForOrder(id));
        requests.finish(actor.id(), "payments:cancel", key, "V2_ORDER", id, result);
        return result;
    }

    /**
     * Trusted refund worker/admin-action port; callers enforce their confirmation/MFA policy. FAILED
     * retries reuse the reserved row/channel identity, UNKNOWN queries.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public RefundOrderVO retryRefund(String token, String id, long expectedVersion, String reason, String key) {
        var actor = identity.authorizeForWrite(token, "ADMIN", false);
        InputValidation.text(reason, 500);
        RefundOrderVO r = refund(id);
        lockPayment(r.paymentOrderId(), null);
        r = refund(id);
        RefundOrderVO replay = requests.replay(actor.id(), "refunds:retry", key, List.of(id, expectedVersion, reason), RefundOrderVO.class);
        if (replay != null)
            return replay;
        version(r.version(), expectedVersion);
        Result actual = channel.query("REFUND", id);
        if (!r.status().equals("SUCCEEDED") && actual == Result.FAILED) {
            channel.retryRefund(id);
            mapper.markRefundProcessing(Long.valueOf(id));
            event(r.orderId(), actor.id(), "REFUND_RETRIED", key, Map.of("refundId", id, "reason", reason));
        } else
            applyRefund(r, actual, "query:refund:" + id + ":" + channel.revision("REFUND", id));
        audit(actor.id(), "REFUND_RETRY", id, key, reason);
        RefundOrderVO result = refund(id);
        requests.finish(actor.id(), "refunds:retry", key, "REFUND_ORDER", id, result);
        return result;
    }

    /**
     * M05 approval port. Caller derives delta from approved server pricing/evidence, holds its review
     * lock, and calls this in the SAME transaction. No HTTP mapping.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public AdjustmentFundsVO approvedAdjustment(String token, String orderId, String adjustmentId, long deltaCents, long expectedOrderVersion, String reason, String key) {
        var actor = identity.authorizeForWrite(token, "ADMIN", false);
        lockNotificationRecipient(orderId);
        var order = orders.read(orderId, true);
        InputValidation.text(reason, 500);
        ResourceIds.parse(adjustmentId);
        if (deltaCents == 0 || deltaCents == Long.MIN_VALUE)
            throw IdentityFailure.invalid();
        var content = List.of(orderId, adjustmentId, deltaCents, expectedOrderVersion, reason);
        AdjustmentFundsVO replay = requests.replay(actor.id(), "payments:adjustment", key, content, AdjustmentFundsVO.class);
        if (replay != null)
            return replay;
        version(order.version(), expectedOrderVersion);
        if (mapper.countAdjustmentById(Long.valueOf(adjustmentId)) > 0)
            throw conflict();
        if (!Set.of("PENDING_ASSIGNMENT", "ACCEPTED", "PICKED_UP", "DELIVERED_PENDING_CONFIRMATION").contains(order.status()))
            throw conflict();
        if (mapper.countPendingAdjustments(Long.valueOf(orderId)) > 0)
            throw new IdentityFailure(409, "ADJUSTMENT_ALREADY_OPEN");
        long effective;
        try {
            effective = Math.addExact(order.effectiveFeeCents(), deltaCents);
        } catch (ArithmeticException overflow) {
            throw IdentityFailure.invalid();
        }
        if (effective <= 0)
            throw IdentityFailure.invalid();
        PaymentOrderVO original = paymentsForOrder(orderId).stream().filter(p -> p.purpose().equals("ORIGINAL")).findFirst().orElseThrow(PaymentServiceImpl::conflict);
        if (!original.status().equals("PAID"))
            throw conflict();
        mapper.insertApprovedAdjustment(Long.valueOf(adjustmentId), Long.valueOf(orderId), deltaCents, deltaCents > 0 ? "PENDING_SUPPLEMENT" : "PENDING_REFUND", Long.valueOf(actor.id()), reason, now());
        mapper.updateEffectiveFeeAndClearConfirmation(effective, now(), Long.valueOf(orderId));
        PaymentOrderVO supplement = null;
        if (deltaCents > 0) {
            String id = mapper.insertSupplementPayment(Long.valueOf(orderId), Long.valueOf(adjustmentId), deltaCents, now().plusMinutes(15));
            supplement = payment(id, false);
        } else {
            long remaining = -deltaCents;
            // Refund each actual receipt; no invented balance when earlier supplements exist.
            for (PaymentOrderVO p : paymentsForOrder(orderId)) if (p.status().equals("PAID")) {
                payment(p.id(), true);
                String source = credited(p.id());
                long amount = Math.min(remaining, available(source, p.amountCents()));
                if (amount > 0) {
                    createRefund(p, source, adjustmentId, amount, "DOWNGRADE", "downgrade:" + adjustmentId + ":" + source, reason);
                    remaining -= amount;
                }
            }
            if (remaining != 0)
                throw new IdentityFailure(409, "AMOUNT_EXCEEDS_AVAILABLE");
        }
        audit(actor.id(), "ADJUSTMENT_FUNDS", adjustmentId, key, reason);
        event(orderId, actor.id(), "ADJUSTMENT_FUNDS_CREATED", adjustmentId, Map.of("deltaCents", deltaCents));
        notifyUser(orderId, "adjustment:" + adjustmentId, "ADJUSTMENT_FUNDS_CREATED");
        AdjustmentFundsVO result = new AdjustmentFundsVO(adjustmentId, supplement, refundsForOrder(orderId).stream().filter(r -> adjustmentId.equals(r.adjustmentId())).toList());
        requests.finish(actor.id(), "payments:adjustment", key, "PAYMENT_ADJUSTMENT", adjustmentId, result);
        return result;
    }

    @Transactional
    @Override
    public int closeExpired() {
        var ids = mapper.findExpiredUnpaidOrderIds(now());
        int closed = 0;
        for (String id : ids) {
            var order = orders.read(id, true);
            if (order.status().equals("PENDING_PAYMENT") && !now().isBefore(order.pendingPaymentDeadline())) {
                closeOrder(id);
                mapper.closeUnpaidOriginalPayments(now(), Long.valueOf(id));
                event(id, null, "PAYMENT_EXPIRED", id, Map.of());
                closed++;
            }
        }
        return closed;
    }

    // All paths (callback, query, and timeout recovery) converge here under order/payment locks.
    private void applyPayment(PaymentOrderVO stale, PaymentAttemptVO a, Result result, String eventId) {
        if (seen("PAYMENT", a.id(), result, eventId) || a.status().equals("SUCCESS"))
            return;
        PaymentOrderVO p = payment(stale.id(), false);
        String status = result.name();
        if (a.status().equals("FAILED") && result != Result.SUCCESS)
            return;
        if (!a.status().equals(status))
            mapper.updateAttemptResult(status, now(), Long.valueOf(a.id()));
        if (result != Result.SUCCESS) {
            if (!Set.of("PAID", "CLOSED").contains(p.status()))
                paymentStatus(p.id(), result == Result.PROCESSING ? "PROCESSING" : status);
            return;
        }
        var order = orders.read(p.orderId(), true);
        boolean closed = order.status().equals("CANCELLED") || p.status().equals("CLOSED");
        if (originalExpired(p) && order.status().equals("PENDING_PAYMENT")) {
            closeOrder(p.orderId());
            paymentStatus(p.id(), "CLOSED");
            closed = true;
            event(p.orderId(), null, "PAYMENT_EXPIRED", p.orderId(), Map.of());
        }
        if (p.purpose().equals("SUPPLEMENT") && !adjustmentOpen(p.adjustmentId()))
            closed = true;
        if (closed || p.status().equals("PAID")) {
            createRefund(p, a.id(), null, p.amountCents(), closed ? "LATE_PAYMENT" : "EXTRA_RECEIPT", "receipt:" + a.id(), "late or additional simulated receipt");
            event(p.orderId(), null, "PAYMENT_COMPENSATION_REQUIRED", a.id(), Map.of("attemptId", a.id()));
            return;
        }
        if (p.purpose().equals("ORIGINAL") && !order.status().equals("PENDING_PAYMENT"))
            throw conflict();
        mapper.markPaymentPaid(now(), Long.valueOf(a.id()), now(), Long.valueOf(p.id()));
        if (p.purpose().equals("ORIGINAL")) {
            // Payment receipt and task-pool admission are committed atomically (payment rules §2).
            mapper.markOrderPendingAssignment(now(), Long.valueOf(p.orderId()));
        } else
            resolveAdjustment(p.adjustmentId());
        financial(p.orderId(), "receipt:" + a.id(), p.purpose() + "_RECEIPT", p.amountCents(), Map.of("paymentId", p.id(), "attemptId", a.id()));
        event(p.orderId(), null, "PAYMENT_SUCCEEDED", a.id(), Map.of("paymentId", p.id()));
        notifyUser(p.orderId(), "payment:" + a.id(), "PAYMENT_SUCCEEDED");
    }

    private void applyRefund(RefundOrderVO r, Result result, String eventId) {
        if (seen("REFUND", r.id(), result, eventId) || r.status().equals("SUCCEEDED"))
            return;
        String status = result == Result.SUCCESS ? "SUCCEEDED" : result.name();
        // Failed refunds retain their balance reservation: a late receipt must never
        // over-refund after another request reused that money. Retry/query the SAME row.
        if (!r.status().equals(status))
            mapper.updateRefundResult(status, result == Result.SUCCESS ? now() : null, Long.valueOf(r.id()));
        if (result == Result.SUCCESS) {
            if (r.adjustmentId() != null && mapper.countUnfinishedAdjustmentRefunds(Long.valueOf(r.adjustmentId())) == 0)
                resolveAdjustment(r.adjustmentId());
            refundFinancial(r);
            event(r.orderId(), null, "REFUND_SUCCEEDED", r.id(), Map.of("refundId", r.id()));
            notifyUser(r.orderId(), "refund:" + r.id(), "REFUND_SUCCEEDED");
        }
    }

    private RefundOrderVO createRefund(PaymentOrderVO p, String source, String adjustment, long cents, String purpose, String businessKey, String reason) {
        var existing = mapper.findRefundIdsByBusinessKey(businessKey);
        if (!existing.isEmpty())
            return refund(existing.get(0));
        if (cents <= 0 || cents > available(source, p.amountCents()))
            throw new IdentityFailure(409, "AMOUNT_EXCEEDS_AVAILABLE");
        if (!attempt(source).status().equals("SUCCESS"))
            throw conflict();
        String id = mapper.insertRefund(Long.valueOf(p.orderId()), Long.valueOf(p.id()), Long.valueOf(source), adjustment == null ? null : Long.valueOf(adjustment), cents, purpose, reason, businessKey, "mock-refund-" + UUID.randomUUID());
        channel.submit("REFUND", id);
        event(p.orderId(), null, "REFUND_REQUESTED", id, Map.of("refundId", id, "amountCents", cents));
        notifyUser(p.orderId(), "refund-request:" + id, "REFUND_REQUESTED");
        return refund(id);
    }

    private void resolveAdjustment(String id) {
        // M05 consumes this event before starting its full ten-minute window; M03
        // cannot decide whether another review/after-sales case still blocks completion.
        if (mapper.completePendingAdjustment(now(), Long.valueOf(id)) > 0) {
            String orderId = mapper.findAdjustmentOrderId(Long.valueOf(id));
            event(orderId, null, "ADJUSTMENT_FUNDS_RESOLVED", id, Map.of("adjustmentId", id));
        }
    }

    private boolean adjustmentOpen(String id) {
        return mapper.isSupplementPending(Long.valueOf(id));
    }

    private long available(String source, long received) {
        return received - mapper.sumReservedRefundCents(Long.valueOf(source));
    }

    private String credited(String payment) {
        return mapper.findCreditedAttemptId(Long.valueOf(payment));
    }

    private void closeOrder(String id) {
        mapper.cancelOrderAndClearConfirmation(now(), now(), Long.valueOf(id));
    }

    private boolean originalExpired(PaymentOrderVO p) {
        return p.purpose().equals("ORIGINAL") && !now().isBefore(p.deadline());
    }

    private PaymentOrderVO lockPayment(String id, String owner) {
        PaymentOrderVO p = payment(id, false);
        // Identity writes lock the user's account first. Acquire the outbox FK lock
        // before the order as well, so a callback cannot deadlock a user cancellation.
        lockNotificationRecipient(p.orderId());
        var order = orders.read(p.orderId(), true);
        if (owner != null)
            own(owner, order);
        return payment(id, true);
    }

    private void lockNotificationRecipient(String orderId) {
        mapper.lockOrderNotificationRecipient(ResourceIds.parse(orderId));
    }

    private void paymentStatus(String id, String status) {
        mapper.updatePaymentStatusIfDifferent(status, now(), Long.valueOf(id), status);
    }

    private PaymentOrderVO payment(String id, boolean lock) {
        var rows = mapper.findPayment(lock, ResourceIds.parse(id));
        if (rows.isEmpty())
            throw missing();
        return rows.get(0);
    }

    private PaymentAttemptVO attempt(String id) {
        var rows = mapper.findAttempt(ResourceIds.parse(id));
        if (rows.isEmpty())
            throw missing();
        return rows.get(0);
    }

    private RefundOrderVO refund(String id) {
        var rows = mapper.findRefund(ResourceIds.parse(id));
        if (rows.isEmpty())
            throw missing();
        return rows.get(0);
    }

    private List<PaymentAttemptVO> attempts(String id) {
        return mapper.findAttemptIds(Long.valueOf(id)).stream().map(this::attempt).toList();
    }

    private List<PaymentAttemptVO> active(String id) {
        return attempts(id).stream().filter(a -> Set.of("PROCESSING", "UNKNOWN").contains(a.status())).toList();
    }

    private List<PaymentOrderVO> paymentsForOrder(String id) {
        return mapper.findPaymentIdsByOrder(Long.valueOf(id)).stream().map(p -> payment(p, false)).toList();
    }

    private List<RefundOrderVO> refundsForOrder(String id) {
        return mapper.findRefundIdsByOrder(Long.valueOf(id)).stream().map(this::refund).toList();
    }

    private PaymentSummaryVO summary(PaymentOrderVO p) {
        return new PaymentSummaryVO(p, attempts(p.id()), refundsForOrder(p.orderId()).stream().filter(r -> r.paymentOrderId().equals(p.id())).toList());
    }

    private void own(String actor, OrderVO order) {
        if (!actor.equals(order.userId()))
            throw missing();
    }

    private OffsetDateTime now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }

    private static IdentityFailure conflict() {
        return new IdentityFailure(409, "ORDER_STATE_CONFLICT");
    }

    private static IdentityFailure missing() {
        return new IdentityFailure(404, "RESOURCE_NOT_FOUND");
    }

    private static void version(long actual, Long expected) {
        if (expected == null || expected < 0)
            throw IdentityFailure.invalid();
        if (actual != expected)
            throw conflict();
    }

    private static PaymentVersionRequest required(PaymentVersionRequest body) {
        if (body == null || body.expectedVersion() == null)
            throw IdentityFailure.invalid();
        return body;
    }

    private void validateResult(PaymentResultRequest body) {
        if (body == null || body.result() == null || body.expectedVersion() == null || body.expectedVersion() < 0)
            throw IdentityFailure.invalid();
        InputValidation.text(body.eventId(), 128);
    }

    private boolean seen(String type, String id, Result result, String eventId) {
        if (knownEvent(type, id, result, eventId))
            return true;
        mapper.insertResultEvent(eventId, type, Long.valueOf(id), result.name());
        return false;
    }

    private boolean knownEvent(String type, String id, Result result, String eventId) {
        // Event IDs are global, including across orders and administrator accounts.
        mapper.lockResultEvent("payment-event:" + eventId);
        var rows = mapper.findResultEvent(eventId);
        if (!rows.isEmpty()) {
            var row = rows.get(0);
            if (!type.equals(row.get("resource_type")) || !id.equals(row.get("resource_id").toString()) || !result.name().equals(row.get("result")))
                throw new IdentityFailure(409, "IDEMPOTENCY_KEY_CONFLICT");
            return true;
        }
        return false;
    }

    private void refundFinancial(RefundOrderVO r) {
        String source = mapper.findRefundSourceAttemptId(Long.valueOf(r.id()));
        long received = payment(r.paymentOrderId(), false).amountCents();
        long total = mapper.sumSucceededRefundCents(Long.valueOf(source));
        long originalPlatform = received / 100 * 30 + (received % 100) * 30 / 100;
        long target = BigInteger.valueOf(total).multiply(BigInteger.valueOf(originalPlatform)).divide(BigInteger.valueOf(received)).longValueExact();
        long previous = mapper.sumRecordedPlatformRefundCents(source);
        long platform = target - previous;
        mapper.insertRefundFinancialEvent(Long.valueOf(r.orderId()), "refund:" + r.id(), r.purpose() + "_REFUND", r.amountCents(), platform, r.amountCents() - platform, json.write(Map.of("refundId", r.id(), "paymentId", r.paymentOrderId(), "sourceAttemptId", source)));
    }

    private void event(String order, String actor, String type, String key, Object payload) {
        mapper.insertOrderEventIfAbsent(Long.valueOf(order), actor == null ? null : Long.valueOf(actor), type, key, json.write(payload));
    }

    private void financial(String order, String key, String kind, long cents, Object payload) {
        long platform = cents / 100 * 30 + (cents % 100) * 30 / 100;
        mapper.insertFinancialEventIfAbsent(Long.valueOf(order), key, kind, cents, platform, cents - platform, json.write(payload));
    }

    private void notifyUser(String order, String key, String type) {
        mapper.insertOrderNotificationIfAbsent(key, json.write(Map.of("orderId", order, "kind", type)), Long.valueOf(order));
    }

    private void audit(String actor, String action, String id, String key, String reason) {
        mapper.insertAdminAudit(Long.valueOf(actor), action, Long.valueOf(id), key, reason);
    }
}
