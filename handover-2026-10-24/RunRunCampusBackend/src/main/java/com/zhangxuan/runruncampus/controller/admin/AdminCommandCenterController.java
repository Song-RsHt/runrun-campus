package com.zhangxuan.runruncampus.controller.admin;

import com.zhangxuan.runruncampus.enums.admin.AdminPermission;
import com.zhangxuan.runruncampus.enums.commandcenter.Queue;
import com.zhangxuan.runruncampus.service.admin.AdminCommandCenterService;
import com.zhangxuan.runruncampus.service.auth.AuthService;
import com.zhangxuan.runruncampus.vo.commandcenter.CommandCenterSnapshotVO;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 后台指挥台 HTTP 入口；绑定请求并委托业务服务，不直接访问数据库。 */
@RestController
@RequestMapping("/api/v1/admin/command-center")
public class AdminCommandCenterController {

    private final AuthService authService;
    private final AdminCommandCenterService service;

    public AdminCommandCenterController(
            AuthService authService, AdminCommandCenterService service) {
        this.authService = authService;
        this.service = service;
    }

    @GetMapping
    public CommandCenterSnapshotVO snapshot(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(defaultValue = "LATE_WAITING") Queue queue,
            @RequestParam(defaultValue = "0") int orderPage,
            @RequestParam(defaultValue = "0") int courierPage) {
        var admin = authService.requirePermission(authorization, AdminPermission.DASHBOARD_VIEW);
        return service.snapshot(admin.getAdminRole(), queue, orderPage, courierPage);
    }
}
