package com.zhangxuan.runruncampus.controller;

import com.zhangxuan.runruncampus.dto.CreateFeedbackRequest;
import com.zhangxuan.runruncampus.service.AuthService;
import com.zhangxuan.runruncampus.service.FeedbackService;
import com.zhangxuan.runruncampus.vo.FeedbackResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;















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
    public FeedbackResponse submit(@RequestHeader("Authorization") String authorization, @Valid @RequestBody CreateFeedbackRequest request) {
        return feedbackService.submit(authService.requireUser(authorization), request.type(), request.content());
    }
}
