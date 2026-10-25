package com.zhangxuan.runruncampus.message;

import com.zhangxuan.runruncampus.auth.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/messages")
public class OrderMessageController {
    private final AuthService authService;
    private final OrderMessageService messageService;

    public OrderMessageController(AuthService authService, OrderMessageService messageService) {
        this.authService = authService;
        this.messageService = messageService;
    }

    @GetMapping
    List<OrderMessageService.MessageResponse> list(@RequestHeader("Authorization") String authorization) {
        return messageService.list(authService.requireUser(authorization));
    }

    @PostMapping("/{id}/read")
    OrderMessageService.MessageResponse markRead(@RequestHeader("Authorization") String authorization,
                                                  @PathVariable Long id) {
        return messageService.markRead(authService.requireUser(authorization), id);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markAllRead(@RequestHeader("Authorization") String authorization) {
        messageService.markAllRead(authService.requireUser(authorization));
    }
}
