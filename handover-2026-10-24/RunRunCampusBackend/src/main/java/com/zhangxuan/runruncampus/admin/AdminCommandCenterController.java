package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.auth.AuthService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/command-center")
public class AdminCommandCenterController {
    private final AuthService authService;
    private final AdminCommandCenterService service;

    public AdminCommandCenterController(AuthService authService, AdminCommandCenterService service) {
        this.authService = authService;
        this.service = service;
    }

    @GetMapping
    AdminCommandCenterService.Snapshot snapshot(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(defaultValue = "LATE_WAITING") AdminCommandCenterService.Queue queue,
            @RequestParam(defaultValue = "0") int orderPage,
            @RequestParam(defaultValue = "0") int courierPage) {
        var admin = authService.requirePermission(authorization, AdminPermission.DASHBOARD_VIEW);
        return service.snapshot(admin.getAdminRole(), queue, orderPage, courierPage);
    }
}
