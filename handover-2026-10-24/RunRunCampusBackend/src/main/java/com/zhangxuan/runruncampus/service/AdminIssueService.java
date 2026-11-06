package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.CampusOrder;
import com.zhangxuan.runruncampus.entity.OrderIssue;
import com.zhangxuan.runruncampus.enums.AdminAuditAction;
import com.zhangxuan.runruncampus.enums.AdminAuditTargetType;
import com.zhangxuan.runruncampus.enums.OrderIssueHandlingStatus;
import com.zhangxuan.runruncampus.enums.OrderIssueType;
import com.zhangxuan.runruncampus.enums.OrderMessageType;
import com.zhangxuan.runruncampus.model.Settings;
import com.zhangxuan.runruncampus.repository.OrderIssueRepository;
import com.zhangxuan.runruncampus.vo.AdminIssueItem;
import com.zhangxuan.runruncampus.vo.AdminIssuePageResponse;
import java.time.Duration;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface AdminIssueService {

    AdminIssuePageResponse list(String query, OrderIssueType type, OrderIssueHandlingStatus handlingStatus, int page, int size);

    AdminIssuePageResponse list(String query, OrderIssueType type, OrderIssueHandlingStatus handlingStatus, boolean overdueOnly, int page, int size);

    AdminIssueItem claim(AppUser administrator, Long id);

    AdminIssueItem resolve(AppUser administrator, Long id, String resolution);
}
