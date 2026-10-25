package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.auth.AuthService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

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
    AdminAuditPageResponse list(@RequestHeader("Authorization") String authorization,
                                @RequestParam(required = false) String query,
                                @RequestParam(required = false) AdminAuditAction action,
                                @RequestParam(defaultValue = "0") @Min(0) int page,
                                @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        authService.requirePermission(authorization, AdminPermission.AUDIT_VIEW);
        return adminAuditService.list(query, action, page, size);
    }

    public record AdminAuditPageResponse(
            List<AdminAuditItem> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) { }

    public record AdminAuditItem(
            Long id,
            AdminAuditAction action,
            AdminAuditTargetType targetType,
            Long targetId,
            String summary,
            Long administratorId,
            String administratorName,
            String administratorPhone,
            Instant createdAt
    ) { }
}
