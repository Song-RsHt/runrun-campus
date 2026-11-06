package com.zhangxuan.runruncampus.controller.message;

import com.zhangxuan.runruncampus.service.auth.AuthService;
import com.zhangxuan.runruncampus.service.message.OrderMessageService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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
  public List<OrderMessageService.MessageResponse> list(
      @RequestHeader("Authorization") String authorization) {
    return messageService.list(authService.requireUser(authorization));
  }

  @PostMapping("/{id}/read")
  public OrderMessageService.MessageResponse markRead(
      @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
    return messageService.markRead(authService.requireUser(authorization), id);
  }

  @PostMapping("/read-all")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void markAllRead(@RequestHeader("Authorization") String authorization) {
    messageService.markAllRead(authService.requireUser(authorization));
  }
}
