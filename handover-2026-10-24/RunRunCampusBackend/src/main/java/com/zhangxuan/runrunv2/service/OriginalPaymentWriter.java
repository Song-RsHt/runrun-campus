package com.zhangxuan.runrunv2.service;

import com.zhangxuan.runrunv2.vo.OriginalPaymentVO;
import java.time.OffsetDateTime;





/**
 * M03 implements this port in the caller's transaction; it must never commit independently.
 */
public interface OriginalPaymentWriter {

    boolean available();

    OriginalPaymentVO create(String orderId, long amountCents, OffsetDateTime deadline);
}
