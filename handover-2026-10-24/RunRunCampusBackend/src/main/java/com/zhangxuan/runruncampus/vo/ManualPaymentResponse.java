package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.CampusOrder;
import com.zhangxuan.runruncampus.enums.CourierSettlementStatus;
import com.zhangxuan.runruncampus.enums.ManualPaymentStatus;
import com.zhangxuan.runruncampus.enums.OrderPaymentMethod;
import com.zhangxuan.runruncampus.enums.OrderPaymentStatus;
import com.zhangxuan.runruncampus.enums.OrderStatus;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.enums.admin.AdminAuditAction;
import com.zhangxuan.runruncampus.enums.admin.AdminAuditTargetType;
import com.zhangxuan.runruncampus.enums.message.OrderMessageType;
import com.zhangxuan.runruncampus.repository.OrderRepository;
import com.zhangxuan.runruncampus.service.admin.AdminAuditService;
import com.zhangxuan.runruncampus.service.message.OrderMessageService;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

public record ManualPaymentResponse(Long orderId, ManualPaymentStatus status, int amountCents, String payerReference, Instant requestedAt, Instant submittedAt, Instant reviewedAt, String reviewerName, String reviewNote, CourierSettlementStatus settlementStatus, Instant settledAt, String settlementReviewerName) {
}
