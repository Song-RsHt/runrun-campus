package com.zhangxuan.runruncampus.service.impl;

import com.zhangxuan.runruncampus.entity.AdminAuditLog;
import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.enums.AdminAuditAction;
import com.zhangxuan.runruncampus.enums.AdminAuditTargetType;
import com.zhangxuan.runruncampus.repository.AdminAuditLogRepository;
import com.zhangxuan.runruncampus.vo.AdminAuditItem;
import com.zhangxuan.runruncampus.vo.AdminAuditPageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.zhangxuan.runruncampus.service.AdminAuditService;

@Service
public class AdminAuditServiceImpl implements AdminAuditService {

    private final AdminAuditLogRepository auditLogRepository;

    public AdminAuditServiceImpl(AdminAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public void record(AppUser administrator, AdminAuditAction action, AdminAuditTargetType targetType, Long targetId, String summary) {
        String safeSummary = summary.length() <= 300 ? summary : summary.substring(0, 300);
        auditLogRepository.save(new AdminAuditLog(administrator, action, targetType, targetId, safeSummary));
    }

    @Transactional(readOnly = true)
    @Override
    public AdminAuditPageResponse list(String query, AdminAuditAction action, int page, int size) {
        String normalizedQuery = normalize(query);
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Direction.DESC, "createdAt", "id"));
        Page<AdminAuditLog> logs = normalizedQuery == null ? auditLogRepository.filter(action, pageable) : auditLogRepository.search(normalizedQuery, action, pageable);
        return new AdminAuditPageResponse(logs.getContent().stream().map(this::item).toList(), logs.getNumber(), logs.getSize(), logs.getTotalElements(), logs.getTotalPages());
    }

    private AdminAuditItem item(AdminAuditLog log) {
        AppUser administrator = log.getAdministrator();
        return new AdminAuditItem(log.getId(), log.getAction(), log.getTargetType(), log.getTargetId(), log.getSummary(), administrator.getId(), administrator.getDisplayName(), administrator.getPhone(), log.getCreatedAt());
    }

    private String normalize(String query) {
        if (query == null || query.isBlank())
            return null;
        return query.trim();
    }
}
