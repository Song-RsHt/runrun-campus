package com.zhangxuan.runruncampus.controller;

import com.zhangxuan.runruncampus.enums.AdminPermission;
import com.zhangxuan.runruncampus.enums.Queue;
import com.zhangxuan.runruncampus.service.AdminCommandCenterService;
import com.zhangxuan.runruncampus.service.AuthService;
import com.zhangxuan.runruncampus.vo.CommandCenterSnapshotVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

























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
    public CommandCenterSnapshotVO snapshot(@RequestHeader("Authorization") String authorization, @RequestParam(defaultValue = "LATE_WAITING") Queue queue, @RequestParam(defaultValue = "0") int orderPage, @RequestParam(defaultValue = "0") int courierPage) {
        var admin = authService.requirePermission(authorization, AdminPermission.DASHBOARD_VIEW);
        return service.snapshot(admin.getAdminRole(), queue, orderPage, courierPage);
    }
}
