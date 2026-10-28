package com.zhangxuan.runrunv2;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/notifications")
@IdentityPermission
public class NotificationController {
    private final IdentityService identity;
    private final NotificationService notifications;
    public NotificationController(IdentityService identity, NotificationService notifications) {
        this.identity = identity; this.notifications = notifications;
    }
    @GetMapping
    public List<NotificationService.Notification> list(HttpServletRequest request,
            @RequestParam(defaultValue="20") int limit) {
        IdentityService.Account a = identity.authenticate(IdentityWebConfiguration.bearer(request));
        return notifications.list(Long.parseLong(a.id()), limit);
    }
    @PostMapping("/{id}/read")
    public void read(HttpServletRequest request, @PathVariable long id) {
        IdentityService.Account a = identity.authenticate(IdentityWebConfiguration.bearer(request));
        notifications.markRead(Long.parseLong(a.id()), id);
    }
}
