package com.zhangxuan.runrunv2.service;

import com.zhangxuan.runrunv2.dto.CancelRequest;
import com.zhangxuan.runrunv2.dto.ResultRequest;
import com.zhangxuan.runrunv2.dto.VersionRequest;
import com.zhangxuan.runrunv2.vo.AdjustmentFundsVO;
import com.zhangxuan.runrunv2.vo.CancelledOrderVO;
import com.zhangxuan.runrunv2.vo.OriginalPaymentVO;
import com.zhangxuan.runrunv2.vo.PaymentAttemptVO;
import com.zhangxuan.runrunv2.vo.PaymentSummaryVO;
import com.zhangxuan.runrunv2.vo.RefundOrderVO;
import com.zhangxuan.runrunv2.vo.StartedPaymentVO;
import java.time.OffsetDateTime;





































/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface PaymentService extends OriginalPaymentWriter {

    boolean available();

    OriginalPaymentVO create(String orderId, long amountCents, OffsetDateTime deadline);

    PaymentSummaryVO get(String token, String paymentId);

    StartedPaymentVO start(String token, String paymentId, String key, VersionRequest body);

    PaymentSummaryVO queryPayment(String token, String id, String key, VersionRequest body);

    PaymentAttemptVO paymentResult(String token, String id, String key, ResultRequest body);

    RefundOrderVO refundResult(String token, String id, String key, ResultRequest body);

    RefundOrderVO queryRefund(String token, String id, String key, VersionRequest body);

    CancelledOrderVO cancel(String token, String id, String key, CancelRequest body);

    /**
     * Trusted refund worker/admin-action port; callers enforce their confirmation/MFA policy. FAILED
     * retries reuse the reserved row/channel identity, UNKNOWN queries.
     */
    RefundOrderVO retryRefund(String token, String id, long expectedVersion, String reason, String key);

    /**
     * M05 approval port. Caller derives delta from approved server pricing/evidence, holds its review
     * lock, and calls this in the SAME transaction. No HTTP mapping.
     */
    AdjustmentFundsVO approvedAdjustment(String token, String orderId, String adjustmentId, long deltaCents, long expectedOrderVersion, String reason, String key);

    int closeExpired();
}
