package com.zhangxuan.runruncampus.controller;

import com.zhangxuan.runruncampus.enums.AdminPermission;
import com.zhangxuan.runruncampus.enums.PushDeliveryStatus;
import com.zhangxuan.runruncampus.service.AdminPushDeliveryService;
import com.zhangxuan.runruncampus.service.AuthService;
import com.zhangxuan.runruncampus.vo.PushDeliveryOverview;
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

    private final AdminPushDeliveryService deliveries;

    public AdminPushDeliveryController(AuthService auth, AdminPushDeliveryService deliveries) {
        this.auth = auth;
        this.deliveries = deliveries;
    }

    @GetMapping
    public PushDeliveryOverview overview(@RequestHeader("Authorization") String authorization, @RequestParam(defaultValue = "DEAD") PushDeliveryStatus status, @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        auth.requirePermission(authorization, AdminPermission.PUSH_DELIVERY_VIEW);
        return deliveries.overview(status, page, size);
    }
}
