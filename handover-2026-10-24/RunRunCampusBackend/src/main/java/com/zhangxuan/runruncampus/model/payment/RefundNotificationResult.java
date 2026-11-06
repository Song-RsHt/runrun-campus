package com.zhangxuan.runruncampus.model.payment;

import com.zhangxuan.runruncampus.enums.payment.RefundStatus;

/** 渠道退款通知核验结果；仅承载内部协作所需的数据或校验逻辑。 */
public record RefundNotificationResult(
        String outTradeNo,
        String outRefundNo,
        String refundId,
        int refundCents,
        RefundStatus status) {}
