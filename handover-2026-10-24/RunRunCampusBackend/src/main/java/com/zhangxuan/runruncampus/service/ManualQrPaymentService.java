package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.CampusOrder;
import com.zhangxuan.runruncampus.enums.CourierSettlementStatus;
import com.zhangxuan.runruncampus.enums.ManualPaymentStatus;
import com.zhangxuan.runruncampus.enums.OrderPaymentMethod;
import com.zhangxuan.runruncampus.enums.OrderPaymentStatus;
import com.zhangxuan.runruncampus.enums.OrderStatus;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.enums.AdminAuditAction;
import com.zhangxuan.runruncampus.enums.AdminAuditTargetType;
import com.zhangxuan.runruncampus.enums.OrderMessageType;
import com.zhangxuan.runruncampus.repository.OrderRepository;
import com.zhangxuan.runruncampus.service.AdminAuditService;
import com.zhangxuan.runruncampus.service.OrderMessageService;
import com.zhangxuan.runruncampus.vo.ManualPaymentResponse;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
