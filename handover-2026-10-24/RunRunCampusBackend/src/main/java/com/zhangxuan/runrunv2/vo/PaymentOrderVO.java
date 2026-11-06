package com.zhangxuan.runrunv2.vo;

import java.time.OffsetDateTime;
import java.util.List;

public record PaymentOrderVO(String id, String orderId, String adjustmentId, String purpose, long amountCents, String status, OffsetDateTime deadline, OffsetDateTime paidAt, long version) {
}
