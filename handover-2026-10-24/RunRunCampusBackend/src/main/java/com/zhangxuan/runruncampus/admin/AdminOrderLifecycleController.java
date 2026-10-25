package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.auth.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderLifecycleController {
    private final AuthService auth;
    private final AdminOrderLifecycleService service;

    public AdminOrderLifecycleController(AuthService auth, AdminOrderLifecycleService service) {
        this.auth = auth;
        this.service = service;
    }

    @PostMapping("/{id}/force-complete")
    ActionResult forceComplete(@RequestHeader("Authorization") String authorization, @PathVariable Long id,
                       @Valid @RequestBody ReasonRequest request) {
        service.forceComplete(auth.requirePermission(authorization, AdminPermission.ORDER_FORCE_COMPLETE),
                id, request.reason());
        return new ActionResult(id, "FORCE_COMPLETED");
    }

    @PostMapping("/{id}/archive")
    ActionResult archive(@RequestHeader("Authorization") String authorization, @PathVariable Long id,
                 @Valid @RequestBody ReasonRequest request) {
        service.archive(auth.requirePermission(authorization, AdminPermission.ORDER_ARCHIVE), id, request.reason());
        return new ActionResult(id, "ARCHIVED");
    }

    @PostMapping("/{id}/unarchive")
    ActionResult unarchive(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        service.unarchive(auth.requirePermission(authorization, AdminPermission.ORDER_ARCHIVE), id);
        return new ActionResult(id, "UNARCHIVED");
    }

    public record ReasonRequest(@NotBlank @Size(min = 5, max = 200) String reason) { }
    public record ActionResult(Long orderId, String action) { }
}
