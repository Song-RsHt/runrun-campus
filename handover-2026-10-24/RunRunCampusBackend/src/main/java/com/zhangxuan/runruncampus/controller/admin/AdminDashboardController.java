package com.zhangxuan.runruncampus.controller.admin;

import com.zhangxuan.runruncampus.enums.admin.AdminPermission;
import com.zhangxuan.runruncampus.service.admin.AdminDashboardService;
import com.zhangxuan.runruncampus.service.auth.AuthService;
import com.zhangxuan.runruncampus.vo.admin.AdminDashboardResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 后台业务概览 HTTP 入口；绑定请求并委托业务服务，不直接访问数据库。 */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class AdminDashboardController {

    private final AuthService authService;
    private final AdminDashboardService dashboardService;

    public AdminDashboardController(
            AuthService authService, AdminDashboardService dashboardService) {
        this.authService = authService;
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public AdminDashboardResponse overview(@RequestHeader("Authorization") String authorization) {
        authService.requirePermission(authorization, AdminPermission.DASHBOARD_VIEW);
        return dashboardService.overview();
    }
}
