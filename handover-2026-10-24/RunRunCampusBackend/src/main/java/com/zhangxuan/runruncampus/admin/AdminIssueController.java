package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.auth.AuthService;
import com.zhangxuan.runruncampus.order.OrderIssueType;
import com.zhangxuan.runruncampus.order.OrderIssueHandlingStatus;
import com.zhangxuan.runruncampus.order.OrderStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.Instant;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/admin/issues")
public class AdminIssueController {
    private final AuthService authService;
    private final AdminIssueService adminIssueService;

    public AdminIssueController(AuthService authService, AdminIssueService adminIssueService) {
        this.authService = authService;
        this.adminIssueService = adminIssueService;
    }

    @GetMapping
    AdminIssuePageResponse list(@RequestHeader("Authorization") String authorization,
                                @RequestParam(required = false) String query,
                                @RequestParam(required = false) OrderIssueType type,
                                @RequestParam(required = false) OrderIssueHandlingStatus handlingStatus,
                                @RequestParam(defaultValue = "false") boolean overdueOnly,
                                @RequestParam(defaultValue = "0") @Min(0) int page,
                                @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        authService.requirePermission(authorization, AdminPermission.ISSUE_VIEW);
        return adminIssueService.list(query, type, handlingStatus, overdueOnly, page, size);
    }

    @PostMapping("/{id}/claim")
    AdminIssueItem claim(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        return adminIssueService.claim(authService.requirePermission(authorization, AdminPermission.ISSUE_MANAGE), id);
    }

    @PostMapping("/{id}/resolve")
    AdminIssueItem resolve(@RequestHeader("Authorization") String authorization, @PathVariable Long id,
                           @Valid @RequestBody ResolveIssueRequest request) {
        return adminIssueService.resolve(
                authService.requirePermission(authorization, AdminPermission.ISSUE_MANAGE), id, request.resolution());
    }

    public record AdminIssuePageResponse(
            List<AdminIssueItem> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            long overdueTotal
    ) { }

    public record AdminIssueItem(
            Long id,
            OrderIssueType type,
            String description,
            Instant createdAt,
            Long orderId,
            String orderNo,
            OrderStatus orderStatus,
            Long reporterId,
            String reporterName,
            String reporterPhone,
            OrderIssueHandlingStatus handlingStatus,
            Long assigneeId,
            String assigneeName,
            String resolution,
            Instant handledAt,
            Instant updatedAt,
            Instant slaDeadlineAt,
            boolean overdue,
            long overdueMinutes
    ) { }

    public record ResolveIssueRequest(
            @NotBlank(message = "请输入处理结果")
            @Size(max = 500, message = "处理结果不能超过500字") String resolution
    ) { }
}
