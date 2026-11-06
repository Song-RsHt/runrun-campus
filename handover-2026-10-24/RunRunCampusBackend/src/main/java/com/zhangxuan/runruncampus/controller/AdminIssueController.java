package com.zhangxuan.runruncampus.controller;

import com.zhangxuan.runruncampus.dto.ResolveIssueRequest;
import com.zhangxuan.runruncampus.enums.AdminPermission;
import com.zhangxuan.runruncampus.enums.OrderIssueHandlingStatus;
import com.zhangxuan.runruncampus.enums.OrderIssueType;
import com.zhangxuan.runruncampus.service.AdminIssueService;
import com.zhangxuan.runruncampus.service.AuthService;
import com.zhangxuan.runruncampus.vo.AdminIssueItem;
import com.zhangxuan.runruncampus.vo.AdminIssuePageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;























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
    public AdminIssuePageResponse list(@RequestHeader("Authorization") String authorization, @RequestParam(required = false) String query, @RequestParam(required = false) OrderIssueType type, @RequestParam(required = false) OrderIssueHandlingStatus handlingStatus, @RequestParam(defaultValue = "false") boolean overdueOnly, @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        authService.requirePermission(authorization, AdminPermission.ISSUE_VIEW);
        return adminIssueService.list(query, type, handlingStatus, overdueOnly, page, size);
    }

    @PostMapping("/{id}/claim")
    public AdminIssueItem claim(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        return adminIssueService.claim(authService.requirePermission(authorization, AdminPermission.ISSUE_MANAGE), id);
    }

    @PostMapping("/{id}/resolve")
    public AdminIssueItem resolve(@RequestHeader("Authorization") String authorization, @PathVariable Long id, @Valid @RequestBody ResolveIssueRequest request) {
        return adminIssueService.resolve(authService.requirePermission(authorization, AdminPermission.ISSUE_MANAGE), id, request.resolution());
    }
}
