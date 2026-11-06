package com.zhangxuan.runrunv2.vo;

import java.time.OffsetDateTime;
import java.util.List;

public record PaymentAttemptVO(String id, String paymentOrderId, int attemptNo, String status, long version) {
}
