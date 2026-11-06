package com.zhangxuan.runruncampus.model.payment;

import com.zhangxuan.runruncampus.enums.payment.RefundStatus;

public record RefundResult(String refundId, RefundStatus status) {}
