package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.CampusOrder;
import com.zhangxuan.runruncampus.enums.MembershipPlanType;
import com.zhangxuan.runruncampus.model.NotificationHeaders;
import com.zhangxuan.runruncampus.vo.PaymentResponse;
import com.zhangxuan.runruncampus.vo.RefundResponse;
import java.util.List;



































/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface PaymentService {

    PaymentResponse createOrderPayment(AppUser customer, Long orderId);

    PaymentResponse createMembershipPayment(AppUser customer, MembershipPlanType plan);

    PaymentResponse status(AppUser customer, String outTradeNo);

    void acceptPaymentNotification(NotificationHeaders headers, String body);

    void acceptRefundNotification(NotificationHeaders headers, String body);

    void closeAndRefundForCancellation(CampusOrder order);

    void refundPriceDifference(CampusOrder order, int refundCents);

    List<RefundResponse> refundsForOrder(CampusOrder order);
}
