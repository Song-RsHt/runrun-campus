package com.zhangxuan.runruncampus.service.impl;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.UserFeedback;
import com.zhangxuan.runruncampus.enums.FeedbackHandlingStatus;
import com.zhangxuan.runruncampus.enums.FeedbackType;
import com.zhangxuan.runruncampus.repository.FeedbackRepository;
import com.zhangxuan.runruncampus.vo.FeedbackResponse;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.zhangxuan.runruncampus.exception.BusinessFailure;
import com.zhangxuan.runruncampus.service.FeedbackService;

@Service
public class FeedbackServiceImpl implements FeedbackService {

    private final FeedbackRepository feedbackRepository;

    public FeedbackServiceImpl(FeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public List<FeedbackResponse> list(AppUser user) {
        return feedbackRepository.findByUserOrderByCreatedAtDesc(user).stream().map(this::response).toList();
    }

    @Transactional
    @Override
    public FeedbackResponse submit(AppUser user, FeedbackType type, String content) {
        String trimmedContent = content == null ? "" : content.trim();
        if (trimmedContent.isBlank()) {
            throw new BusinessFailure(400, "请输入反馈内容");
        }
        return response(feedbackRepository.save(new UserFeedback(user, type, trimmedContent)));
    }

    private FeedbackResponse response(UserFeedback feedback) {
        return new FeedbackResponse(feedback.getId(), feedback.getType(), feedback.getContent(), feedback.getCreatedAt(), feedback.getHandlingStatus(), feedback.getResolution(), feedback.getHandledAt(), feedback.getUpdatedAt());
    }
}
