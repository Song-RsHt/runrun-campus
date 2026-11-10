package com.zhangxuan.runruncampus.controller.admin;

import com.zhangxuan.runruncampus.enums.admin.AdminAuditAction;
import com.zhangxuan.runruncampus.enums.admin.AdminPermission;
import com.zhangxuan.runruncampus.service.admin.AdminAuditService;
import com.zhangxuan.runruncampus.service.auth.AuthService;
import com.zhangxuan.runruncampus.vo.admin.AdminAuditPageResponse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理员操作审计 HTTP 入口；绑定请求并委托业务服务，不直接访问数据库。 */
@Validated
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AdminAuditController {

    private final AuthService authService;
    private final AdminAuditService adminAuditService;

    public AdminAuditController(AuthService authService, AdminAuditService adminAuditService) {
        this.authService = authService;
        this.adminAuditService = adminAuditService;
    }

    @GetMapping
    public AdminAuditPageResponse list(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) AdminAuditAction action,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        authService.requirePermission(authorization, AdminPermission.AUDIT_VIEW);
        return adminAuditService.list(query, action, page, size);
    }
}
