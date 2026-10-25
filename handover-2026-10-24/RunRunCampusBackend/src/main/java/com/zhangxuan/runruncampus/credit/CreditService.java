package com.zhangxuan.runruncampus.credit;

import com.zhangxuan.runruncampus.admin.AdminAuditAction;
import com.zhangxuan.runruncampus.admin.AdminAuditService;
import com.zhangxuan.runruncampus.admin.AdminAuditTargetType;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRepository;
import com.zhangxuan.runruncampus.user.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class CreditService {
    private final CreditProfileRepository profiles;
    private final CreditEventRepository events;
    private final RiskSignalRepository signals;
    private final UserRepository users;
    private final AdminAuditService audit;

    public CreditService(CreditProfileRepository profiles, CreditEventRepository events,
                         RiskSignalRepository signals, UserRepository users, AdminAuditService audit) {
        this.profiles = profiles;
        this.events = events;
        this.signals = signals;
        this.users = users;
        this.audit = audit;
    }

    @Transactional
    public ProfileResponse profile(AppUser user, int page, int size) {
        if (user.getRole() == UserRole.ADMIN || user.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "该账号不参与信用体系");
        }
        return profileResponse(user, profileFor(user), page, size);
    }

    @Transactional
    public ProfileResponse profileForAdmin(Long userId, int page, int size) {
        AppUser user = requireNonAdminUser(userId);
        return profileResponse(user, profileFor(user), page, size);
    }

    @Transactional
    public ChangeResponse adjust(AppUser administrator, Long userId, int delta, String reason) {
        if (delta == 0 || delta < -100 || delta > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "信用分调整必须在-100至100之间且不能为0");
        }
        String normalized = reason(reason);
        AppUser user = requireNonAdminUser(userId);
        CreditProfile profile = lockedProfile(user);
        int applied = profile.apply(delta);
        if (applied == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "信用分已达到边界，无法继续调整");
        CreditEvent event = events.save(new CreditEvent(user, administrator, CreditEventType.ADMIN_ADJUSTMENT,
                applied, profile.getScore(), normalized, null, null));
        audit.record(administrator, AdminAuditAction.CREDIT_ADJUSTED, AdminAuditTargetType.CREDIT_EVENT,
                userId, "调整信用分%+d：%s".formatted(applied, normalized));
        return change(event, profile);
    }

    @Transactional
    public ChangeResponse reverse(AppUser administrator, Long userId, Long eventId, String reason) {
        String normalized = reason(reason);
        AppUser user = requireNonAdminUser(userId);
        CreditEvent original = events.findByIdAndUserId(eventId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "信用记录不存在"));
        if (original.getType() == CreditEventType.REVERSAL || events.existsByOriginalEventId(eventId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该信用记录已经撤销或不能撤销");
        }
        CreditProfile profile = lockedProfile(user);
        int applied = profile.apply(-original.getDelta());
        CreditEvent reversal = events.save(new CreditEvent(user, administrator, CreditEventType.REVERSAL,
                applied, profile.getScore(), normalized, null, original.getId()));
        audit.record(administrator, AdminAuditAction.CREDIT_EVENT_REVERSED, AdminAuditTargetType.CREDIT_EVENT,
                original.getId(), "撤销信用记录：" + normalized);
        return change(reversal, profile);
    }

    @Transactional
    public ChangeResponse confirmSignal(AppUser administrator, Long signalId, int delta, String reason) {
        if (delta >= 0 || delta < -100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "确认风险时扣分必须在-100至-1之间");
        }
        String normalized = reason(reason);
        RiskSignal signal = pendingSignal(signalId);
        AppUser user = signal.getSubject();
        CreditProfile profile = lockedProfile(user);
        int applied = profile.apply(delta);
        if (applied == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "用户信用分已为0分");
        signal.review(RiskSignalStatus.CONFIRMED, administrator, normalized);
        CreditEvent event = events.save(new CreditEvent(user, administrator, CreditEventType.RISK_CONFIRMED,
                applied, profile.getScore(), normalized, signal.getId(), null));
        audit.record(administrator, AdminAuditAction.RISK_SIGNAL_CONFIRMED, AdminAuditTargetType.RISK_SIGNAL,
                signalId, "确认风险并扣分%+d：%s".formatted(applied, normalized));
        return change(event, profile);
    }

    @Transactional
    public SignalItem dismissSignal(AppUser administrator, Long signalId, String note) {
        String normalized = reason(note);
        RiskSignal signal = pendingSignal(signalId);
        signal.review(RiskSignalStatus.DISMISSED, administrator, normalized);
        audit.record(administrator, AdminAuditAction.RISK_SIGNAL_DISMISSED, AdminAuditTargetType.RISK_SIGNAL,
                signalId, "驳回风险信号：" + normalized);
        return signalItem(signal);
    }

    @Transactional(readOnly = true)
    public SignalPage signals(RiskSignalStatus status, int page, int size) {
        Page<RiskSignal> result = status == null
                ? signals.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
                : signals.findByStatusOrderByCreatedAtAsc(status, PageRequest.of(page, size));
        return new SignalPage(result.getContent().stream().map(this::signalItem).toList(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private ProfileResponse profileResponse(AppUser user, CreditProfile profile, int page, int size) {
        Page<CreditEvent> eventPage = events.findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(page, size));
        List<EventItem> items = eventPage.getContent().stream().map(this::eventItem).toList();
        return new ProfileResponse(user.getId(), user.getDisplayName(), profile.getScore(), profile.getRiskLevel(),
                signals.countBySubjectIdAndStatus(user.getId(), RiskSignalStatus.PENDING), items,
                eventPage.getNumber(), eventPage.getSize(), eventPage.getTotalElements(), eventPage.getTotalPages());
    }

    private CreditProfile profileFor(AppUser user) {
        return profiles.findByUserId(user.getId()).orElseGet(() -> profiles.save(new CreditProfile(user)));
    }

    private CreditProfile lockedProfile(AppUser user) {
        return profiles.findLockedByUserId(user.getId()).orElseGet(() -> profiles.save(new CreditProfile(user)));
    }

    private AppUser requireNonAdminUser(Long id) {
        AppUser user = users.findLockedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        if (user.getRole() == UserRole.ADMIN || user.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "该账号不参与信用体系");
        }
        return user;
    }

    private RiskSignal pendingSignal(Long id) {
        RiskSignal signal = signals.findLockedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "风险信号不存在"));
        if (signal.getStatus() != RiskSignalStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "风险信号已经处理");
        }
        return signal;
    }

    private String reason(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() < 5 || normalized.length() > 300) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "处理原因需为5至300个字符");
        }
        return normalized;
    }

    private ChangeResponse change(CreditEvent event, CreditProfile profile) {
        return new ChangeResponse(event.getId(), event.getType(), event.getDelta(), event.getBalanceAfter(),
                event.getReason(), event.getCreatedAt(), profile.getScore(), profile.getRiskLevel());
    }

    private EventItem eventItem(CreditEvent event) {
        return new EventItem(event.getId(), event.getType(), event.getDelta(), event.getBalanceAfter(),
                event.getReason(), event.getOperator().getDisplayName(), event.getRiskSignalId(),
                event.getOriginalEventId(), event.getCreatedAt());
    }

    private SignalItem signalItem(RiskSignal signal) {
        return new SignalItem(signal.getId(), signal.getSubject().getId(), signal.getSubject().getDisplayName(),
                signal.getSubject().getPhone(), signal.getType(), signal.getStatus(),
                signal.getOrder() == null ? null : signal.getOrder().getId(), signal.getDetails(),
                signal.getCreatedAt(), signal.getReviewedAt(),
                signal.getReviewedBy() == null ? null : signal.getReviewedBy().getDisplayName(), signal.getReviewNote());
    }

    public record ProfileResponse(Long userId, String displayName, int score, CreditRiskLevel riskLevel,
                                  long pendingSignals, List<EventItem> events, int page, int size,
                                  long totalElements, int totalPages) { }
    public record EventItem(Long id, CreditEventType type, int delta, int balanceAfter, String reason,
                            String operatorName, Long riskSignalId, Long originalEventId, Instant createdAt) { }
    public record ChangeResponse(Long id, CreditEventType type, int delta, int balanceAfter, String reason,
                                 Instant createdAt, int score, CreditRiskLevel riskLevel) { }
    public record SignalItem(Long id, Long userId, String displayName, String phone, RiskSignalType type,
                             RiskSignalStatus status, Long orderId, String details, Instant createdAt,
                             Instant reviewedAt, String reviewerName, String reviewNote) { }
    public record SignalPage(List<SignalItem> items, int page, int size, long totalElements, int totalPages) { }
}
