package com.zhangxuan.runrunv2.vo;

import java.util.List;

/** 支付义务、支付尝试及退款汇总响应；保持现有 JSON 字段及序列化契约。 */
public record PaymentSummaryVO(
        PaymentOrderVO payment, List<PaymentAttemptVO> attempts, List<RefundOrderVO> refunds) {}
