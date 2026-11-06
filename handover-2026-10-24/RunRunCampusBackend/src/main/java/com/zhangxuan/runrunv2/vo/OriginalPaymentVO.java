package com.zhangxuan.runrunv2.vo;

import java.time.OffsetDateTime;











public record OriginalPaymentVO(String id, String orderId, String purpose, long amountCents, String status, OffsetDateTime deadline, long version) {
}
