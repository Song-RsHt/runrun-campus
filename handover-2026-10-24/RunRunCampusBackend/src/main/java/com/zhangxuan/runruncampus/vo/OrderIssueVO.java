package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.CampusOrder;
import com.zhangxuan.runruncampus.entity.OrderIssue;
import com.zhangxuan.runruncampus.enums.OrderIssueHandlingStatus;
import com.zhangxuan.runruncampus.enums.OrderIssueType;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.enums.message.OrderMessageType;
import com.zhangxuan.runruncampus.repository.OrderIssueRepository;
import com.zhangxuan.runruncampus.repository.OrderRepository;
import com.zhangxuan.runruncampus.service.message.OrderMessageService;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

public record OrderIssueVO(Long id, OrderIssueType type, String description, String reporterName, UserRole reporterRole, Instant createdAt, OrderIssueHandlingStatus handlingStatus, String resolution, Instant handledAt, Instant updatedAt) {
}
