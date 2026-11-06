package com.zhangxuan.runruncampus.controller.feedback;

import com.zhangxuan.runruncampus.dto.feedback.CreateFeedbackRequest;
import com.zhangxuan.runruncampus.service.auth.AuthService;
import com.zhangxuan.runruncampus.service.feedback.FeedbackService;
import com.zhangxuan.runruncampus.vo.feedback.FeedbackResponse;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 用户反馈 HTTP 入口；绑定请求并委托业务服务，不直接访问数据库。 */
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
    public List<FeedbackResponse> list(@RequestHeader("Authorization") String authorization) {
        return feedbackService.list(authService.requireUser(authorization));
    }

    @PostMapping
    public FeedbackResponse submit(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody CreateFeedbackRequest request) {
        return feedbackService.submit(
                authService.requireUser(authorization), request.type(), request.content());
    }
}
