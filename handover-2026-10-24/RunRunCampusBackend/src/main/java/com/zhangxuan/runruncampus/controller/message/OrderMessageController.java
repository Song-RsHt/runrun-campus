package com.zhangxuan.runruncampus.controller.message;

import com.zhangxuan.runruncampus.service.auth.AuthService;
import com.zhangxuan.runruncampus.service.message.OrderMessageService;
import com.zhangxuan.runruncampus.vo.message.OrderMessageVO;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 订单站内消息 HTTP 入口；绑定请求并委托业务服务，不直接访问数据库。 */
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
    public List<OrderMessageVO> list(@RequestHeader("Authorization") String authorization) {
        return messageService.list(authService.requireUser(authorization));
    }

    @PostMapping("/{id}/read")
    public OrderMessageVO markRead(
            @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        return messageService.markRead(authService.requireUser(authorization), id);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@RequestHeader("Authorization") String authorization) {
        messageService.markAllRead(authService.requireUser(authorization));
    }
}
