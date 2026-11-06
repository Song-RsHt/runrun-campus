package com.zhangxuan.runrunv2.vo;

import java.time.OffsetDateTime;





public record RefundOrderVO(String id, String orderId, String paymentOrderId, String adjustmentId, long amountCents, String purpose, String status, OffsetDateTime succeededAt, long version) {
}
