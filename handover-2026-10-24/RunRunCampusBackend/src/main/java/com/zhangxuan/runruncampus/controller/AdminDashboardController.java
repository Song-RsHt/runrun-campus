package com.zhangxuan.runruncampus.controller;

import com.zhangxuan.runruncampus.enums.AdminPermission;
import com.zhangxuan.runruncampus.service.AdminDashboardService;
import com.zhangxuan.runruncampus.service.AuthService;
import com.zhangxuan.runruncampus.vo.AdminDashboardResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class AdminDashboardController {

    private final AuthService authService;

    private final AdminDashboardService dashboardService;

    public AdminDashboardController(AuthService authService, AdminDashboardService dashboardService) {
        this.authService = authService;
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public AdminDashboardResponse overview(@RequestHeader("Authorization") String authorization) {
        authService.requirePermission(authorization, AdminPermission.DASHBOARD_VIEW);
        return dashboardService.overview();
    }
}
