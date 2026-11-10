package com.zhangxuan.runrunv2.controller.notification;

import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.security.IdentityPermission;
import com.zhangxuan.runrunv2.service.notification.NotificationService;
import com.zhangxuan.runrunv2.vo.notification.NotificationVO;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 站内通知及订阅投递 HTTP 入口；绑定请求并委托业务服务，不直接访问数据库。 */
@RestController
@IdentityPermission
@RequestMapping("/api/v2/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public List<NotificationVO> list(@RequestHeader("Authorization") String auth) {
        return service.list(token(auth));
    }

    @PostMapping("/{id}/read")
    public Map<String, Object> read(
            @RequestHeader("Authorization") String auth, @PathVariable long id) {
        service.markRead(token(auth), id);
        return Map.of("id", id, "read", true);
    }

    private static String token(String value) {
        if (value == null || !value.startsWith("Bearer ")) {
            throw IdentityFailure.auth();
        }
        return value.substring(7);
    }
}
