package com.zhangxuan.runrunv2.vo;

import com.zhangxuan.runrunv2.enums.ServiceType;
import com.zhangxuan.runrunv2.model.Address;
import com.zhangxuan.runrunv2.model.Pricing;
import com.zhangxuan.runrunv2.model.Schedule;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record CreatedOrderVO(OrderVO order, OriginalPaymentVO originalPayment) {
}
