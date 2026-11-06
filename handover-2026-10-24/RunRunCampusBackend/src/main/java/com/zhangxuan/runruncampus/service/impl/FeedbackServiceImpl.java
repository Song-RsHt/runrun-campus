package com.zhangxuan.runruncampus.service.impl;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.UserFeedback;
import com.zhangxuan.runruncampus.enums.FeedbackType;
import com.zhangxuan.runruncampus.exception.BusinessFailure;
import com.zhangxuan.runruncampus.repository.FeedbackRepository;
import com.zhangxuan.runruncampus.service.FeedbackService;
import com.zhangxuan.runruncampus.vo.FeedbackResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 用户反馈业务实现；通过注入的业务契约及数据访问组件执行，事务边界保留在本类。 */
@Service
public class FeedbackServiceImpl implements FeedbackService {

    private final FeedbackRepository feedbackRepository;

    public FeedbackServiceImpl(FeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public List<FeedbackResponse> list(AppUser user) {
        return feedbackRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(this::response)
                .toList();
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
}
