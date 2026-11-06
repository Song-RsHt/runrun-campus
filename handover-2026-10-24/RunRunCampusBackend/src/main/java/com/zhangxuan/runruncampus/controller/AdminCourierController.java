package com.zhangxuan.runruncampus.controller;

import com.zhangxuan.runruncampus.enums.AdminPermission;
import com.zhangxuan.runruncampus.enums.CourierCertificationStatus;
import com.zhangxuan.runruncampus.service.AdminCourierService;
import com.zhangxuan.runruncampus.service.AuthService;
import com.zhangxuan.runruncampus.vo.AdminCourierPageResponse;
import com.zhangxuan.runruncampus.vo.AdminCourierPerformanceResponse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理员骑手查询 HTTP 入口；绑定请求并委托业务服务，不直接访问数据库。 */
@Validated
@RestController
@RequestMapping("/api/v1/admin/couriers")
public class AdminCourierController {

    private final AuthService authService;
    private final AdminCourierService adminCourierService;

    public AdminCourierController(
            AuthService authService, AdminCourierService adminCourierService) {
        this.authService = authService;
        this.adminCourierService = adminCourierService;
    }

    @GetMapping
    public AdminCourierPageResponse list(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) CourierCertificationStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        authService.requirePermission(authorization, AdminPermission.COURIER_VIEW);
        return adminCourierService.list(query, status, page, size);
    }

    @GetMapping("/{courierId}/performance")
    public AdminCourierPerformanceResponse performance(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long courierId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        authService.requirePermission(authorization, AdminPermission.COURIER_VIEW);
        return adminCourierService.performance(courierId, page, size);
    }
}
