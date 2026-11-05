package com.zhangxuan.runrunv2;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@IdentityPermission
@RequestMapping("/api/v2/notifications")
public class NotificationController {
    private final NotificationService service;
    public NotificationController(NotificationService service) { this.service = service; }

    @GetMapping
    public List<Map<String,Object>> list(@RequestHeader("Authorization") String auth) {
        return service.list(token(auth));
    }

    @PostMapping("/{id}/read")
    public Map<String,Object> read(@RequestHeader("Authorization") String auth, @PathVariable long id) {
        service.markRead(token(auth), id);
        return Map.of("id", id, "read", true);
    }

    private static String token(String value) {
        if (value == null || !value.startsWith("Bearer ")) throw IdentityFailure.auth();
        return value.substring(7);
    }
}
