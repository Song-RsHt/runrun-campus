package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.config.WechatPayProperties;
import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.CampusOrder;
import com.zhangxuan.runruncampus.entity.WechatPayment;
import com.zhangxuan.runruncampus.entity.WechatRefund;
import com.zhangxuan.runruncampus.enums.MembershipPlanType;
import com.zhangxuan.runruncampus.enums.OrderPaymentMethod;
import com.zhangxuan.runruncampus.enums.OrderPaymentStatus;
import com.zhangxuan.runruncampus.enums.OrderStatus;
import com.zhangxuan.runruncampus.enums.PaymentBusinessType;
import com.zhangxuan.runruncampus.enums.PaymentStatus;
import com.zhangxuan.runruncampus.enums.RefundStatus;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.integration.payment.WechatPayGateway;
import com.zhangxuan.runruncampus.model.AppPayParameters;
import com.zhangxuan.runruncampus.model.NotificationHeaders;
import com.zhangxuan.runruncampus.model.RefundNotificationResult;
import com.zhangxuan.runruncampus.model.RefundResult;
import com.zhangxuan.runruncampus.model.TradeResult;
import com.zhangxuan.runruncampus.repository.OrderRepository;
import com.zhangxuan.runruncampus.repository.WechatPaymentRepository;
import com.zhangxuan.runruncampus.repository.WechatRefundRepository;
import com.zhangxuan.runruncampus.vo.AppPaymentParameters;
import com.zhangxuan.runruncampus.vo.PaymentResponse;
import com.zhangxuan.runruncampus.vo.RefundResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
