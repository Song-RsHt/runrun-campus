package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.feedback.*;
import com.zhangxuan.runruncampus.user.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminFeedbackService {
    private final FeedbackRepository feedbackRepository;
    private final AdminAuditService auditService;

    public AdminFeedbackService(FeedbackRepository feedbackRepository, AdminAuditService auditService) {
        this.feedbackRepository = feedbackRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public AdminFeedbackController.FeedbackPageResponse list(
            String query, FeedbackType type, FeedbackHandlingStatus handlingStatus, int page, int size) {
        String normalizedQuery = normalize(query);
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<UserFeedback> feedback = normalizedQuery == null
                ? feedbackRepository.filterForAdmin(type, handlingStatus, pageable)
                : feedbackRepository.searchForAdmin(normalizedQuery, type, handlingStatus, pageable);
        return new AdminFeedbackController.FeedbackPageResponse(
                feedback.getContent().stream().map(this::item).toList(), feedback.getNumber(), feedback.getSize(),
                feedback.getTotalElements(), feedback.getTotalPages());
    }

    @Transactional
    public AdminFeedbackController.FeedbackItem claim(AppUser administrator, Long id) {
        UserFeedback feedback = requireFeedback(id);
        if (feedback.getHandlingStatus() == FeedbackHandlingStatus.RESOLVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已解决的反馈不能重新领取");
        }
        if (feedback.getHandlingStatus() == FeedbackHandlingStatus.PROCESSING) {
            if (feedback.getAssignee().getId().equals(administrator.getId())) return item(feedback);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "该反馈已由" + feedback.getAssignee().getDisplayName() + "领取");
        }
        feedback.claim(administrator);
        auditService.record(administrator, AdminAuditAction.FEEDBACK_CLAIMED,
                AdminAuditTargetType.FEEDBACK, feedback.getId(), "领取用户反馈：#" + feedback.getId());
        return item(feedback);
    }

    @Transactional
    public AdminFeedbackController.FeedbackItem resolve(AppUser administrator, Long id, String resolution) {
        UserFeedback feedback = requireFeedback(id);
        if (feedback.getHandlingStatus() != FeedbackHandlingStatus.PROCESSING
                || feedback.getAssignee() == null
                || !feedback.getAssignee().getId().equals(administrator.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先领取该反馈再处理");
        }
        String normalized = requireText(resolution);
        feedback.resolve(normalized);
        auditService.record(administrator, AdminAuditAction.FEEDBACK_RESOLVED,
                AdminAuditTargetType.FEEDBACK, feedback.getId(), "解决用户反馈：#" + feedback.getId());
        return item(feedback);
    }

    private AdminFeedbackController.FeedbackItem item(UserFeedback feedback) {
        AppUser user = feedback.getUser();
        AppUser assignee = feedback.getAssignee();
        return new AdminFeedbackController.FeedbackItem(
                feedback.getId(), feedback.getType(), feedback.getContent(), feedback.getCreatedAt(),
                user.getId(), user.getDisplayName(), user.getPhone(), feedback.getHandlingStatus(),
                assignee == null ? null : assignee.getId(), assignee == null ? null : assignee.getDisplayName(),
                feedback.getResolution(), feedback.getHandledAt(), feedback.getUpdatedAt());
    }

    private UserFeedback requireFeedback(Long id) {
        return feedbackRepository.findLockedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "反馈不存在"));
    }

    private String requireText(String value) {
        if (value == null || value.trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入处理结果");
        }
        return value.trim();
    }

    private String normalize(String query) {
        return query == null || query.isBlank() ? null : query.trim();
    }
}
