package com.zhangxuan.runruncampus.controller.feedback;

import com.zhangxuan.runruncampus.dto.request.feedback.CreateFeedbackRequest;
import com.zhangxuan.runruncampus.service.auth.AuthService;
import com.zhangxuan.runruncampus.service.feedback.FeedbackService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/feedback")
public class FeedbackController {

  private final AuthService authService;

  private final FeedbackService feedbackService;

  public FeedbackController(AuthService authService, FeedbackService feedbackService) {
    this.authService = authService;
    this.feedbackService = feedbackService;
  }

  @GetMapping
  public List<FeedbackService.FeedbackResponse> list(
      @RequestHeader("Authorization") String authorization) {
    return feedbackService.list(authService.requireUser(authorization));
  }

  @PostMapping
  public FeedbackService.FeedbackResponse submit(
      @RequestHeader("Authorization") String authorization,
      @Valid @RequestBody CreateFeedbackRequest request) {
    return feedbackService.submit(
        authService.requireUser(authorization), request.type(), request.content());
  }
}
