package com.zhangxuan.runruncampus.feedback;

import com.zhangxuan.runruncampus.auth.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    List<FeedbackService.FeedbackResponse> list(@RequestHeader("Authorization") String authorization) {
        return feedbackService.list(authService.requireUser(authorization));
    }

    @PostMapping
    FeedbackService.FeedbackResponse submit(@RequestHeader("Authorization") String authorization,
                                             @Valid @RequestBody CreateFeedbackRequest request) {
        return feedbackService.submit(authService.requireUser(authorization), request.type(), request.content());
    }

    public record CreateFeedbackRequest(
            @NotNull(message = "请选择反馈类型") FeedbackType type,
            @NotBlank(message = "请输入反馈内容") @Size(max = 500, message = "反馈内容不能超过500字") String content
    ) { }
}
