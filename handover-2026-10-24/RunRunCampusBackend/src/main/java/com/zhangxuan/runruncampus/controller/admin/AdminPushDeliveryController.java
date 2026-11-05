package com.zhangxuan.runruncampus.controller.admin;
import com.zhangxuan.runruncampus.enums.admin.AdminPermission;

import com.zhangxuan.runruncampus.service.auth.AuthService;
import com.zhangxuan.runruncampus.repository.message.PushDeliveryRepository;
import com.zhangxuan.runruncampus.enums.message.PushDeliveryStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/admin/push-deliveries")
public class AdminPushDeliveryController {
    private final AuthService auth;
    private final PushDeliveryRepository deliveries;

    public AdminPushDeliveryController(AuthService auth, PushDeliveryRepository deliveries) {
        this.auth = auth;
        this.deliveries = deliveries;
    }

    @GetMapping
    public Overview overview(@RequestHeader("Authorization") String authorization,
                      @RequestParam(defaultValue = "DEAD") PushDeliveryStatus status,
                      @RequestParam(defaultValue = "0") @Min(0) int page,
                      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        auth.requirePermission(authorization, AdminPermission.PUSH_DELIVERY_VIEW);
        return new Overview(deliveries.count(PushDeliveryStatus.DEAD),
                deliveries.count(PushDeliveryStatus.PENDING),
                deliveries.count(PushDeliveryStatus.PROCESSING), deliveries.page(status, page, size));
    }

    public record Overview(long dead, long pending, long processing,
                           PushDeliveryRepository.DeliveryPage deliveries) { }
}
