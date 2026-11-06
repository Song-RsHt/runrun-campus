package com.zhangxuan.runrunv2.vo;

import java.util.List;





public record CancelledOrderVO(OrderVO order, List<RefundOrderVO> refunds) {
}
