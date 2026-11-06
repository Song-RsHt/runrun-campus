package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.vo.ManualPaymentResponse;























/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface ManualQrPaymentService {

    ManualPaymentResponse requestPayment(AppUser courier, Long orderId);

    ManualPaymentResponse submitPayment(AppUser customer, Long orderId, String payerReference);

    ManualPaymentResponse confirmPayment(AppUser administrator, Long orderId);

    ManualPaymentResponse rejectPayment(AppUser administrator, Long orderId, String reason);

    ManualPaymentResponse confirmCourierSettlement(AppUser administrator, Long orderId);

    ManualPaymentResponse status(AppUser participant, Long orderId);
}
