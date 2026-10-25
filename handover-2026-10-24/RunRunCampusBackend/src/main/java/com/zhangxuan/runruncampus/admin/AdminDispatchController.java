package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.auth.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminDispatchController {
    private final AuthService authService;
    private final AdminDispatchService service;

    public AdminDispatchController(AuthService authService, AdminDispatchService service) {
        this.authService = authService;
        this.service = service;
    }

    @GetMapping("/{id}/dispatch-candidates")
    AdminDispatchService.Candidates candidates(@RequestHeader("Authorization") String authorization,
                                                @PathVariable Long id,
                                                @RequestParam(defaultValue = "0") int page) {
        authService.requirePermission(authorization, AdminPermission.ORDER_DISPATCH);
        return service.candidates(id, page);
    }

    @PostMapping("/{id}/dispatch")
    AdminDispatchService.DispatchResult dispatch(@RequestHeader("Authorization") String authorization,
                                                  @PathVariable Long id,
                                                  @Valid @RequestBody DispatchRequest request) {
        var administrator = authService.requirePermission(authorization, AdminPermission.ORDER_DISPATCH);
        return service.dispatch(administrator, id, request.courierId());
    }

    public record DispatchRequest(@NotNull Long courierId) { }
}
