package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.auth.AuthService;
import com.zhangxuan.runruncampus.feedback.FeedbackHandlingStatus;
import com.zhangxuan.runruncampus.feedback.FeedbackType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/admin/feedback")
public class AdminFeedbackController {
    private final AuthService authService;
    private final AdminFeedbackService feedbackService;

    public AdminFeedbackController(AuthService authService, AdminFeedbackService feedbackService) {
        this.authService = authService;
        this.feedbackService = feedbackService;
    }

    @GetMapping
    FeedbackPageResponse list(@RequestHeader("Authorization") String authorization,
                              @RequestParam(required = false) String query,
                              @RequestParam(required = false) FeedbackType type,
                              @RequestParam(required = false) FeedbackHandlingStatus handlingStatus,
                              @RequestParam(defaultValue = "0") @Min(0) int page,
                              @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        authService.requirePermission(authorization, AdminPermission.FEEDBACK_VIEW);
        return feedbackService.list(query, type, handlingStatus, page, size);
    }

    @PostMapping("/{id}/claim")
    FeedbackItem claim(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        return feedbackService.claim(
                authService.requirePermission(authorization, AdminPermission.FEEDBACK_MANAGE), id);
    }

    @PostMapping("/{id}/resolve")
    FeedbackItem resolve(@RequestHeader("Authorization") String authorization, @PathVariable Long id,
                         @Valid @RequestBody ResolveFeedbackRequest request) {
        return feedbackService.resolve(
                authService.requirePermission(authorization, AdminPermission.FEEDBACK_MANAGE), id,
                request.resolution());
    }

    public record FeedbackPageResponse(List<FeedbackItem> items, int page, int size,
                                       long totalElements, int totalPages) { }

    public record FeedbackItem(Long id, FeedbackType type, String content, Instant createdAt,
                               Long userId, String userName, String userPhone,
                               FeedbackHandlingStatus handlingStatus, Long assigneeId, String assigneeName,
                               String resolution, Instant handledAt, Instant updatedAt) { }

    public record ResolveFeedbackRequest(
            @NotBlank(message = "请输入处理结果")
            @Size(max = 500, message = "处理结果不能超过500字") String resolution) { }
}
