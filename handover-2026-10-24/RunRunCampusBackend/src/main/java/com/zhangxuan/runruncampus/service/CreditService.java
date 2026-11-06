package com.zhangxuan.runruncampus.service;

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
import com.zhangxuan.runruncampus.vo.CreditChangeVO;
import com.zhangxuan.runruncampus.vo.CreditEventVO;
import com.zhangxuan.runruncampus.vo.CreditProfileVO;
import com.zhangxuan.runruncampus.vo.RiskSignalPageVO;
import com.zhangxuan.runruncampus.vo.RiskSignalVO;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface CreditService {

    CreditProfileVO profile(AppUser user, int page, int size);

    CreditProfileVO profileForAdmin(Long userId, int page, int size);

    CreditChangeVO adjust(AppUser administrator, Long userId, int delta, String reason);

    CreditChangeVO reverse(AppUser administrator, Long userId, Long eventId, String reason);

    CreditChangeVO confirmSignal(AppUser administrator, Long signalId, int delta, String reason);

    RiskSignalVO dismissSignal(AppUser administrator, Long signalId, String note);

    RiskSignalPageVO signals(RiskSignalStatus status, int page, int size);
}
