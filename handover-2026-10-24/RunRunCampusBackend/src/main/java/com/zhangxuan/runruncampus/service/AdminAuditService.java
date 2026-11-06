package com.zhangxuan.runruncampus.service;

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

/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface AdminAuditService {

    void record(AppUser administrator, AdminAuditAction action, AdminAuditTargetType targetType, Long targetId, String summary);

    AdminAuditPageResponse list(String query, AdminAuditAction action, int page, int size);
}
