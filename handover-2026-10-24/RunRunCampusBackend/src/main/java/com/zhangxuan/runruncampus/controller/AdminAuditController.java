package com.zhangxuan.runruncampus.controller;

import com.zhangxuan.runruncampus.enums.AdminAuditAction;
import com.zhangxuan.runruncampus.enums.AdminPermission;
import com.zhangxuan.runruncampus.service.AdminAuditService;
import com.zhangxuan.runruncampus.service.AuthService;
import com.zhangxuan.runruncampus.vo.AdminAuditPageResponse;
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
@RequestMapping("/api/v1/admin/audit-logs")
public class AdminAuditController {

    private final AuthService authService;

    private final AdminAuditService adminAuditService;

    public AdminAuditController(AuthService authService, AdminAuditService adminAuditService) {
        this.authService = authService;
        this.adminAuditService = adminAuditService;
    }

    @GetMapping
    public AdminAuditPageResponse list(@RequestHeader("Authorization") String authorization, @RequestParam(required = false) String query, @RequestParam(required = false) AdminAuditAction action, @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        authService.requirePermission(authorization, AdminPermission.AUDIT_VIEW);
        return adminAuditService.list(query, action, page, size);
    }
}
