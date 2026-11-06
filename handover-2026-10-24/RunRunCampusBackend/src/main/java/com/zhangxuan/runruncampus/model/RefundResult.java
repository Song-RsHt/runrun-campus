package com.zhangxuan.runruncampus.model;

import com.zhangxuan.runruncampus.enums.RefundStatus;

public record RefundResult(String refundId, RefundStatus status) {
}
