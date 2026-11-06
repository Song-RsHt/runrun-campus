package com.zhangxuan.runruncampus.vo;

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
import com.zhangxuan.runruncampus.service.MembershipService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

public record RefundResponse(int amountCents, String reason, RefundStatus status, Instant createdAt, Instant updatedAt) {
}
