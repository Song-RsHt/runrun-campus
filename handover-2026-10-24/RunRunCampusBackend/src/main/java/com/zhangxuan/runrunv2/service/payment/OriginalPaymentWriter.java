package com.zhangxuan.runrunv2.service.payment;

import com.zhangxuan.runrunv2.dto.response.order.OrderModels;
import java.time.OffsetDateTime;

/** M03 implements this port in the caller's transaction; it must never commit independently. */
public interface OriginalPaymentWriter {

  boolean available();

  OrderModels.OriginalPayment create(String orderId, long amountCents, OffsetDateTime deadline);
}
