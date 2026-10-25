package com.zhangxuan.runruncampus.credit;

import com.zhangxuan.runruncampus.admin.AdminPermission;
import com.zhangxuan.runruncampus.auth.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/admin")
public class AdminCreditController {
    private final AuthService auth;
    private final CreditService credit;

    public AdminCreditController(AuthService auth, CreditService credit) {
        this.auth = auth;
        this.credit = credit;
    }

    @GetMapping("/users/{id}/credit")
    CreditService.ProfileResponse profile(@RequestHeader("Authorization") String authorization,
                                           @PathVariable Long id,
                                           @RequestParam(defaultValue = "0") @Min(0) int page,
                                           @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        auth.requirePermission(authorization, AdminPermission.USER_VIEW);
        return credit.profileForAdmin(id, page, size);
    }

    @PostMapping("/users/{id}/credit/adjustments")
    CreditService.ChangeResponse adjust(@RequestHeader("Authorization") String authorization,
                                         @PathVariable Long id, @Valid @RequestBody AdjustmentRequest request) {
        return credit.adjust(auth.requirePermission(authorization, AdminPermission.USER_MANAGE),
                id, request.delta(), request.reason());
    }

    @PostMapping("/users/{id}/credit/events/{eventId}/reversal")
    CreditService.ChangeResponse reverse(@RequestHeader("Authorization") String authorization,
                                          @PathVariable Long id, @PathVariable Long eventId,
                                          @Valid @RequestBody ReasonRequest request) {
        return credit.reverse(auth.requirePermission(authorization, AdminPermission.USER_MANAGE),
                id, eventId, request.reason());
    }

    @GetMapping("/risk-signals")
    CreditService.SignalPage signals(@RequestHeader("Authorization") String authorization,
                                      @RequestParam(required = false) RiskSignalStatus status,
                                      @RequestParam(defaultValue = "0") @Min(0) int page,
                                      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        auth.requirePermission(authorization, AdminPermission.USER_VIEW);
        return credit.signals(status, page, size);
    }

    @PostMapping("/risk-signals/{id}/confirm")
    CreditService.ChangeResponse confirm(@RequestHeader("Authorization") String authorization,
                                          @PathVariable Long id, @Valid @RequestBody AdjustmentRequest request) {
        return credit.confirmSignal(auth.requirePermission(authorization, AdminPermission.USER_MANAGE),
                id, request.delta(), request.reason());
    }

    @PostMapping("/risk-signals/{id}/dismiss")
    CreditService.SignalItem dismiss(@RequestHeader("Authorization") String authorization,
                                      @PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
        return credit.dismissSignal(auth.requirePermission(authorization, AdminPermission.USER_MANAGE),
                id, request.reason());
    }

    public record AdjustmentRequest(@Min(-100) @Max(100) int delta,
                                    @NotBlank @Size(min = 5, max = 300) String reason) { }
    public record ReasonRequest(@NotBlank @Size(min = 5, max = 300) String reason) { }
}
