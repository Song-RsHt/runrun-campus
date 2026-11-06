package com.zhangxuan.runrunv2.vo;

import java.util.List;

/** 调整资金响应；保持现有 JSON 字段及序列化契约。 */
public record AdjustmentFundsVO(
        String adjustmentId, PaymentOrderVO payment, List<RefundOrderVO> refunds) {}
