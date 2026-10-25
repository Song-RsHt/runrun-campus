package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.api.PlatformSettingsService;
import com.zhangxuan.runruncampus.order.CampusOrder;
import com.zhangxuan.runruncampus.order.OrderIssue;
import com.zhangxuan.runruncampus.order.OrderIssueRepository;
import com.zhangxuan.runruncampus.order.OrderIssueType;
import com.zhangxuan.runruncampus.order.OrderIssueHandlingStatus;
import com.zhangxuan.runruncampus.message.OrderMessageService;
import com.zhangxuan.runruncampus.message.OrderMessageType;
import com.zhangxuan.runruncampus.user.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;

@Service
public class AdminIssueService {
    private final OrderIssueRepository issueRepository;
    private final AdminAuditService auditService;
    private final OrderMessageService messageService;
    private final PlatformSettingsService settingsService;

    public AdminIssueService(OrderIssueRepository issueRepository, AdminAuditService auditService,
                             OrderMessageService messageService, PlatformSettingsService settingsService) {
        this.issueRepository = issueRepository;
        this.auditService = auditService;
        this.messageService = messageService;
        this.settingsService = settingsService;
    }

    @Transactional(readOnly = true)
    public AdminIssueController.AdminIssuePageResponse list(
            String query, OrderIssueType type, OrderIssueHandlingStatus handlingStatus, int page, int size) {
        return list(query, type, handlingStatus, false, page, size);
    }

    @Transactional(readOnly = true)
    public AdminIssueController.AdminIssuePageResponse list(
            String query, OrderIssueType type, OrderIssueHandlingStatus handlingStatus,
            boolean overdueOnly, int page, int size) {
        String normalizedQuery = normalize(query);
        PlatformSettingsService.Settings settings = settingsService.get();
        Instant now = Instant.now();
        Instant pendingCutoff = now.minusSeconds(settings.issuePendingMinutes() * 60L);
        Instant processingCutoff = now.minusSeconds(settings.issueProcessingMinutes() * 60L);
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<OrderIssue> issues = find(normalizedQuery, type, handlingStatus, overdueOnly,
                pendingCutoff, processingCutoff, pageable);
        long overdueTotal = overdueOnly ? issues.getTotalElements() : find(normalizedQuery, type, handlingStatus, true,
                pendingCutoff, processingCutoff, PageRequest.of(0, 1)).getTotalElements();
        return new AdminIssueController.AdminIssuePageResponse(
                issues.getContent().stream().map(issue -> item(issue, now, settings)).toList(),
                issues.getNumber(), issues.getSize(), issues.getTotalElements(), issues.getTotalPages(), overdueTotal);
    }

    @Transactional
    public AdminIssueController.AdminIssueItem claim(AppUser administrator, Long id) {
        OrderIssue issue = requireIssue(id);
        if (issue.getHandlingStatus() == OrderIssueHandlingStatus.RESOLVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已解决的工单不能重新领取");
        }
        if (issue.getHandlingStatus() == OrderIssueHandlingStatus.PROCESSING) {
            if (issue.getAssignee().getId().equals(administrator.getId())) return item(issue);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "该工单已由" + issue.getAssignee().getDisplayName() + "领取");
        }
        issue.claim(administrator);
        auditService.record(administrator, AdminAuditAction.ORDER_ISSUE_CLAIMED,
                AdminAuditTargetType.ORDER_ISSUE, issue.getId(), "领取异常工单：" + issue.getOrder().getOrderNo());
        return item(issue);
    }

    @Transactional
    public AdminIssueController.AdminIssueItem resolve(AppUser administrator, Long id, String resolution) {
        OrderIssue issue = requireIssue(id);
        if (issue.getHandlingStatus() != OrderIssueHandlingStatus.PROCESSING
                || issue.getAssignee() == null
                || !issue.getAssignee().getId().equals(administrator.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先领取该工单再处理");
        }
        String normalized = requireText(resolution);
        issue.resolve(normalized);
        messageService.send(issue.getReporter(), issue.getOrder(), OrderMessageType.ORDER_ISSUE,
                "异常工单已处理", "订单 " + issue.getOrder().getOrderNo() + " 的异常处理结果：" + normalized);
        auditService.record(administrator, AdminAuditAction.ORDER_ISSUE_RESOLVED,
                AdminAuditTargetType.ORDER_ISSUE, issue.getId(),
                "解决异常工单：" + issue.getOrder().getOrderNo() + "，结果：" + normalized);
        return item(issue);
    }

    private AdminIssueController.AdminIssueItem item(OrderIssue issue) {
        return item(issue, Instant.now(), settingsService.get());
    }

    private AdminIssueController.AdminIssueItem item(OrderIssue issue, Instant now,
                                                     PlatformSettingsService.Settings settings) {
        CampusOrder order = issue.getOrder();
        AppUser reporter = issue.getReporter();
        AppUser assignee = issue.getAssignee();
        Instant deadline = switch (issue.getHandlingStatus()) {
            case PENDING -> issue.getCreatedAt().plusSeconds(settings.issuePendingMinutes() * 60L);
            case PROCESSING -> issue.getUpdatedAt().plusSeconds(settings.issueProcessingMinutes() * 60L);
            case RESOLVED -> null;
        };
        boolean overdue = deadline != null && now.isAfter(deadline);
        long overdueMinutes = overdue ? Duration.between(deadline, now).toMinutes() : 0;
        return new AdminIssueController.AdminIssueItem(
                issue.getId(), issue.getType(), issue.getDescription(), issue.getCreatedAt(),
                order.getId(), order.getOrderNo(), order.getStatus(), reporter.getId(),
                reporter.getDisplayName(), reporter.getPhone(), issue.getHandlingStatus(),
                assignee == null ? null : assignee.getId(), assignee == null ? null : assignee.getDisplayName(),
                issue.getResolution(), issue.getHandledAt(), issue.getUpdatedAt(), deadline, overdue, overdueMinutes);
    }

    private Page<OrderIssue> find(String query, OrderIssueType type, OrderIssueHandlingStatus handlingStatus,
                                  boolean overdueOnly, Instant pendingCutoff, Instant processingCutoff,
                                  PageRequest pageable) {
        return query == null
                ? issueRepository.filterForAdmin(type, handlingStatus, overdueOnly,
                pendingCutoff, processingCutoff, pageable)
                : issueRepository.searchForAdmin(query, type, handlingStatus, overdueOnly,
                pendingCutoff, processingCutoff, pageable);
    }

    private OrderIssue requireIssue(Long id) {
        return issueRepository.findLockedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "异常工单不存在"));
    }

    private String requireText(String value) {
        if (value == null || value.trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入处理结果");
        }
        return value.trim();
    }

    private String normalize(String query) {
        if (query == null || query.isBlank()) return null;
        return query.trim();
    }
}
