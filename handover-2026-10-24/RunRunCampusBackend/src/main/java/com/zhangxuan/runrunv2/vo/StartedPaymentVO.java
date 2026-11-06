package com.zhangxuan.runrunv2.vo;

import java.time.OffsetDateTime;
import java.util.List;

public record StartedPaymentVO(PaymentOrderVO payment, PaymentAttemptVO attempt) {
}
