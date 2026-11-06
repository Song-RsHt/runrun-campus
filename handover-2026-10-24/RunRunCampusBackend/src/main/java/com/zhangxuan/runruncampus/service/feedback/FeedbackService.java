package com.zhangxuan.runruncampus.service.feedback;

import com.zhangxuan.runruncampus.enums.feedback.FeedbackHandlingStatus;
import com.zhangxuan.runruncampus.enums.feedback.FeedbackType;
import com.zhangxuan.runruncampus.model.feedback.UserFeedback;
import com.zhangxuan.runruncampus.model.user.AppUser;
import com.zhangxuan.runruncampus.repository.feedback.FeedbackRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FeedbackService {

  private final FeedbackRepository feedbackRepository;

  public FeedbackService(FeedbackRepository feedbackRepository) {
    this.feedbackRepository = feedbackRepository;
  }

  @Transactional(readOnly = true)
  public List<FeedbackResponse> list(AppUser user) {
    return feedbackRepository.findByUserOrderByCreatedAtDesc(user).stream()
        .map(this::response)
        .toList();
  }

  @Transactional
  public FeedbackResponse submit(AppUser user, FeedbackType type, String content) {
    String trimmedContent = content == null ? "" : content.trim();
    if (trimmedContent.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入反馈内容");
    }
    return response(feedbackRepository.save(new UserFeedback(user, type, trimmedContent)));
  }

  private FeedbackResponse response(UserFeedback feedback) {
    return new FeedbackResponse(
        feedback.getId(),
        feedback.getType(),
        feedback.getContent(),
        feedback.getCreatedAt(),
        feedback.getHandlingStatus(),
        feedback.getResolution(),
        feedback.getHandledAt(),
        feedback.getUpdatedAt());
  }

  public record FeedbackResponse(
      Long id,
      FeedbackType type,
      String content,
      Instant createdAt,
      FeedbackHandlingStatus handlingStatus,
      String resolution,
      Instant handledAt,
      Instant updatedAt) {}
}
