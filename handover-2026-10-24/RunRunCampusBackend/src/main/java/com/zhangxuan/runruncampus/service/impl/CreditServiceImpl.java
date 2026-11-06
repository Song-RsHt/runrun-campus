package com.zhangxuan.runruncampus.service.impl;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.CreditEvent;
import com.zhangxuan.runruncampus.entity.CreditProfile;
import com.zhangxuan.runruncampus.entity.RiskSignal;
import com.zhangxuan.runruncampus.enums.AdminAuditAction;
import com.zhangxuan.runruncampus.enums.AdminAuditTargetType;
import com.zhangxuan.runruncampus.enums.CreditEventType;
import com.zhangxuan.runruncampus.enums.RiskSignalStatus;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.exception.BusinessFailure;
import com.zhangxuan.runruncampus.repository.CreditEventRepository;
import com.zhangxuan.runruncampus.repository.CreditProfileRepository;
import com.zhangxuan.runruncampus.repository.RiskSignalRepository;
import com.zhangxuan.runruncampus.repository.UserRepository;
import com.zhangxuan.runruncampus.service.AdminAuditService;
import com.zhangxuan.runruncampus.service.CreditService;
import com.zhangxuan.runruncampus.vo.CreditChangeVO;
import com.zhangxuan.runruncampus.vo.CreditEventVO;
import com.zhangxuan.runruncampus.vo.CreditProfileVO;
import com.zhangxuan.runruncampus.vo.RiskSignalPageVO;
import com.zhangxuan.runruncampus.vo.RiskSignalVO;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

































@Service
public class CreditServiceImpl implements CreditService {

    private final CreditProfileRepository profiles;

    private final CreditEventRepository events;

    private final RiskSignalRepository signals;

    private final UserRepository users;

    private final AdminAuditService audit;

    public CreditServiceImpl(CreditProfileRepository profiles, CreditEventRepository events, RiskSignalRepository signals, UserRepository users, AdminAuditService audit) {
        this.profiles = profiles;
        this.events = events;
        this.signals = signals;
        this.users = users;
        this.audit = audit;
    }

    @Transactional
    @Override
    public CreditProfileVO profile(AppUser user, int page, int size) {
        if (user.getRole() == UserRole.ADMIN || user.getDeletedAt() != null) {
            throw new BusinessFailure(403, "该账号不参与信用体系");
        }
        return profileResponse(user, profileFor(user), page, size);
    }

    @Transactional
    @Override
    public CreditProfileVO profileForAdmin(Long userId, int page, int size) {
        AppUser user = requireNonAdminUser(userId);
        return profileResponse(user, profileFor(user), page, size);
    }

    @Transactional
    @Override
    public CreditChangeVO adjust(AppUser administrator, Long userId, int delta, String reason) {
        if (delta == 0 || delta < -100 || delta > 100) {
            throw new BusinessFailure(400, "信用分调整必须在-100至100之间且不能为0");
        }
        String normalized = reason(reason);
        AppUser user = requireNonAdminUser(userId);
        CreditProfile profile = lockedProfile(user);
        int applied = profile.apply(delta);
        if (applied == 0)
            throw new BusinessFailure(409, "信用分已达到边界，无法继续调整");
        CreditEvent event = events.save(new CreditEvent(user, administrator, CreditEventType.ADMIN_ADJUSTMENT, applied, profile.getScore(), normalized, null, null));
        audit.record(administrator, AdminAuditAction.CREDIT_ADJUSTED, AdminAuditTargetType.CREDIT_EVENT, userId, "调整信用分%+d：%s".formatted(applied, normalized));
        return change(event, profile);
    }

    @Transactional
    @Override
    public CreditChangeVO reverse(AppUser administrator, Long userId, Long eventId, String reason) {
        String normalized = reason(reason);
        AppUser user = requireNonAdminUser(userId);
        CreditEvent original = events.findByIdAndUserId(eventId, userId).orElseThrow(() -> new BusinessFailure(404, "信用记录不存在"));
        if (original.getType() == CreditEventType.REVERSAL || events.existsByOriginalEventId(eventId)) {
            throw new BusinessFailure(409, "该信用记录已经撤销或不能撤销");
        }
        CreditProfile profile = lockedProfile(user);
        int applied = profile.apply(-original.getDelta());
        CreditEvent reversal = events.save(new CreditEvent(user, administrator, CreditEventType.REVERSAL, applied, profile.getScore(), normalized, null, original.getId()));
        audit.record(administrator, AdminAuditAction.CREDIT_EVENT_REVERSED, AdminAuditTargetType.CREDIT_EVENT, original.getId(), "撤销信用记录：" + normalized);
        return change(reversal, profile);
    }

    @Transactional
    @Override
    public CreditChangeVO confirmSignal(AppUser administrator, Long signalId, int delta, String reason) {
        if (delta >= 0 || delta < -100) {
            throw new BusinessFailure(400, "确认风险时扣分必须在-100至-1之间");
        }
        String normalized = reason(reason);
        RiskSignal signal = pendingSignal(signalId);
        AppUser user = signal.getSubject();
        CreditProfile profile = lockedProfile(user);
        int applied = profile.apply(delta);
        if (applied == 0)
            throw new BusinessFailure(409, "用户信用分已为0分");
        signal.review(RiskSignalStatus.CONFIRMED, administrator, normalized);
        CreditEvent event = events.save(new CreditEvent(user, administrator, CreditEventType.RISK_CONFIRMED, applied, profile.getScore(), normalized, signal.getId(), null));
        audit.record(administrator, AdminAuditAction.RISK_SIGNAL_CONFIRMED, AdminAuditTargetType.RISK_SIGNAL, signalId, "确认风险并扣分%+d：%s".formatted(applied, normalized));
        return change(event, profile);
    }

    @Transactional
    @Override
    public RiskSignalVO dismissSignal(AppUser administrator, Long signalId, String note) {
        String normalized = reason(note);
        RiskSignal signal = pendingSignal(signalId);
        signal.review(RiskSignalStatus.DISMISSED, administrator, normalized);
        audit.record(administrator, AdminAuditAction.RISK_SIGNAL_DISMISSED, AdminAuditTargetType.RISK_SIGNAL, signalId, "驳回风险信号：" + normalized);
        return signalItem(signal);
    }

    @Transactional(readOnly = true)
    @Override
    public RiskSignalPageVO signals(RiskSignalStatus status, int page, int size) {
        Page<RiskSignal> result = status == null ? signals.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size)) : signals.findByStatusOrderByCreatedAtAsc(status, PageRequest.of(page, size));
        return new RiskSignalPageVO(result.getContent().stream().map(this::signalItem).toList(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private CreditProfileVO profileResponse(AppUser user, CreditProfile profile, int page, int size) {
        Page<CreditEvent> eventPage = events.findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(page, size));
        List<CreditEventVO> items = eventPage.getContent().stream().map(this::eventItem).toList();
        return new CreditProfileVO(user.getId(), user.getDisplayName(), profile.getScore(), profile.getRiskLevel(), signals.countBySubjectIdAndStatus(user.getId(), RiskSignalStatus.PENDING), items, eventPage.getNumber(), eventPage.getSize(), eventPage.getTotalElements(), eventPage.getTotalPages());
    }

    private CreditProfile profileFor(AppUser user) {
        return profiles.findByUserId(user.getId()).orElseGet(() -> profiles.save(new CreditProfile(user)));
    }

    private CreditProfile lockedProfile(AppUser user) {
        return profiles.findLockedByUserId(user.getId()).orElseGet(() -> profiles.save(new CreditProfile(user)));
    }

    private AppUser requireNonAdminUser(Long id) {
        AppUser user = users.findLockedById(id).orElseThrow(() -> new BusinessFailure(404, "用户不存在"));
        if (user.getRole() == UserRole.ADMIN || user.getDeletedAt() != null) {
            throw new BusinessFailure(403, "该账号不参与信用体系");
        }
        return user;
    }

    private RiskSignal pendingSignal(Long id) {
        RiskSignal signal = signals.findLockedById(id).orElseThrow(() -> new BusinessFailure(404, "风险信号不存在"));
        if (signal.getStatus() != RiskSignalStatus.PENDING) {
            throw new BusinessFailure(409, "风险信号已经处理");
        }
        return signal;
    }

    private String reason(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() < 5 || normalized.length() > 300) {
            throw new BusinessFailure(400, "处理原因需为5至300个字符");
        }
        return normalized;
    }

    private CreditChangeVO change(CreditEvent event, CreditProfile profile) {
        return new CreditChangeVO(event.getId(), event.getType(), event.getDelta(), event.getBalanceAfter(), event.getReason(), event.getCreatedAt(), profile.getScore(), profile.getRiskLevel());
    }

    private CreditEventVO eventItem(CreditEvent event) {
        return new CreditEventVO(event.getId(), event.getType(), event.getDelta(), event.getBalanceAfter(), event.getReason(), event.getOperator().getDisplayName(), event.getRiskSignalId(), event.getOriginalEventId(), event.getCreatedAt());
    }

    private RiskSignalVO signalItem(RiskSignal signal) {
        return new RiskSignalVO(signal.getId(), signal.getSubject().getId(), signal.getSubject().getDisplayName(), signal.getSubject().getPhone(), signal.getType(), signal.getStatus(), signal.getOrder() == null ? null : signal.getOrder().getId(), signal.getDetails(), signal.getCreatedAt(), signal.getReviewedAt(), signal.getReviewedBy() == null ? null : signal.getReviewedBy().getDisplayName(), signal.getReviewNote());
    }
}
