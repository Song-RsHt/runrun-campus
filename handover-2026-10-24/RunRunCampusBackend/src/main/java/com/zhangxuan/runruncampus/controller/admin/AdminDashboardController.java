package com.zhangxuan.runruncampus.controller.admin;

import com.zhangxuan.runruncampus.dto.response.admin.dashboard.AdminDashboardResponse;
import com.zhangxuan.runruncampus.enums.admin.AdminPermission;
import com.zhangxuan.runruncampus.service.AuthService;
import com.zhangxuan.runruncampus.service.admin.AdminDashboardService;
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
