package com.zhangxuan.runrunv2.vo;

import java.util.List;





public record AdjustmentFundsVO(String adjustmentId, PaymentOrderVO payment, List<RefundOrderVO> refunds) {
}
