package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.user.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuditService {
    private final AdminAuditLogRepository auditLogRepository;

    public AdminAuditService(AdminAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void record(AppUser administrator, AdminAuditAction action, AdminAuditTargetType targetType,
                       Long targetId, String summary) {
        String safeSummary = summary.length() <= 300 ? summary : summary.substring(0, 300);
        auditLogRepository.save(new AdminAuditLog(
                administrator, action, targetType, targetId, safeSummary));
    }

    @Transactional(readOnly = true)
    public AdminAuditController.AdminAuditPageResponse list(
            String query, AdminAuditAction action, int page, int size) {
        String normalizedQuery = normalize(query);
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<AdminAuditLog> logs = normalizedQuery == null
                ? auditLogRepository.filter(action, pageable)
                : auditLogRepository.search(normalizedQuery, action, pageable);
        return new AdminAuditController.AdminAuditPageResponse(
                logs.getContent().stream().map(this::item).toList(),
                logs.getNumber(), logs.getSize(), logs.getTotalElements(), logs.getTotalPages());
    }

    private AdminAuditController.AdminAuditItem item(AdminAuditLog log) {
        AppUser administrator = log.getAdministrator();
        return new AdminAuditController.AdminAuditItem(
                log.getId(), log.getAction(), log.getTargetType(), log.getTargetId(), log.getSummary(),
                administrator.getId(), administrator.getDisplayName(), administrator.getPhone(), log.getCreatedAt());
    }

    private String normalize(String query) {
        if (query == null || query.isBlank()) return null;
        return query.trim();
    }
}
