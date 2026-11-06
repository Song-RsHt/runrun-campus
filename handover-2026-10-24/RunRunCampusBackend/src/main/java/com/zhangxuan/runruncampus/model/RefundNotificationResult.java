package com.zhangxuan.runruncampus.model;

import com.zhangxuan.runruncampus.enums.RefundStatus;




public record RefundNotificationResult(String outTradeNo, String outRefundNo, String refundId, int refundCents, RefundStatus status) {
}
