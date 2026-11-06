package com.zhangxuan.runruncampus.controller.admin;

import com.zhangxuan.runruncampus.dto.request.admin.feedback.ResolveFeedbackRequest;
import com.zhangxuan.runruncampus.dto.response.admin.feedback.FeedbackItem;
import com.zhangxuan.runruncampus.dto.response.admin.feedback.FeedbackPageResponse;
import com.zhangxuan.runruncampus.enums.admin.AdminPermission;
import com.zhangxuan.runruncampus.enums.feedback.FeedbackHandlingStatus;
import com.zhangxuan.runruncampus.enums.feedback.FeedbackType;
import com.zhangxuan.runruncampus.service.admin.AdminFeedbackService;
import com.zhangxuan.runruncampus.service.auth.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
  public FeedbackPageResponse list(
      @RequestHeader("Authorization") String authorization,
      @RequestParam(required = false) String query,
      @RequestParam(required = false) FeedbackType type,
      @RequestParam(required = false) FeedbackHandlingStatus handlingStatus,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    authService.requirePermission(authorization, AdminPermission.FEEDBACK_VIEW);
    return feedbackService.list(query, type, handlingStatus, page, size);
  }

  @PostMapping("/{id}/claim")
  public FeedbackItem claim(
      @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
    return feedbackService.claim(
        authService.requirePermission(authorization, AdminPermission.FEEDBACK_MANAGE), id);
  }

  @PostMapping("/{id}/resolve")
  public FeedbackItem resolve(
      @RequestHeader("Authorization") String authorization,
      @PathVariable Long id,
      @Valid @RequestBody ResolveFeedbackRequest request) {
    return feedbackService.resolve(
        authService.requirePermission(authorization, AdminPermission.FEEDBACK_MANAGE),
        id,
        request.resolution());
  }
}
