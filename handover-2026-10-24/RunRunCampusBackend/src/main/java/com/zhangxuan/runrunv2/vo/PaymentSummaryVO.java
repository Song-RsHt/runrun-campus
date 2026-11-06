package com.zhangxuan.runrunv2.vo;

import java.time.OffsetDateTime;
import java.util.List;

public record PaymentSummaryVO(PaymentOrderVO payment, List<PaymentAttemptVO> attempts, List<RefundOrderVO> refunds) {
}
