package com.zhangxuan.runrunv2.vo;






public record PaymentAttemptVO(String id, String paymentOrderId, int attemptNo, String status, long version) {
}
