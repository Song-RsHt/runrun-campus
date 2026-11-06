package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.CreditEvent;
import com.zhangxuan.runruncampus.entity.CreditProfile;
import com.zhangxuan.runruncampus.entity.RiskSignal;
import com.zhangxuan.runruncampus.enums.AdminAuditAction;
import com.zhangxuan.runruncampus.enums.AdminAuditTargetType;
import com.zhangxuan.runruncampus.enums.CreditEventType;
import com.zhangxuan.runruncampus.enums.CreditRiskLevel;
import com.zhangxuan.runruncampus.enums.RiskSignalStatus;
import com.zhangxuan.runruncampus.enums.RiskSignalType;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.repository.CreditEventRepository;
import com.zhangxuan.runruncampus.repository.CreditProfileRepository;
import com.zhangxuan.runruncampus.repository.RiskSignalRepository;
import com.zhangxuan.runruncampus.repository.UserRepository;
import com.zhangxuan.runruncampus.service.AdminAuditService;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

public record CreditProfileVO(Long userId, String displayName, int score, CreditRiskLevel riskLevel, long pendingSignals, List<CreditEventVO> events, int page, int size, long totalElements, int totalPages) {
}
