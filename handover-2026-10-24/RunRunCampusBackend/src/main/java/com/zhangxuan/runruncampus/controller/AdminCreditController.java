package com.zhangxuan.runruncampus.controller;

import com.zhangxuan.runruncampus.dto.CreditAdjustmentRequest;
import com.zhangxuan.runruncampus.dto.CreditReasonRequest;
import com.zhangxuan.runruncampus.enums.AdminPermission;
import com.zhangxuan.runruncampus.enums.RiskSignalStatus;
import com.zhangxuan.runruncampus.service.AuthService;
import com.zhangxuan.runruncampus.service.CreditService;
import com.zhangxuan.runruncampus.vo.CreditChangeVO;
import com.zhangxuan.runruncampus.vo.CreditProfileVO;
import com.zhangxuan.runruncampus.vo.RiskSignalPageVO;
import com.zhangxuan.runruncampus.vo.RiskSignalVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
    public CreditProfileVO profile(@RequestHeader("Authorization") String authorization, @PathVariable Long id, @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        auth.requirePermission(authorization, AdminPermission.USER_VIEW);
        return credit.profileForAdmin(id, page, size);
    }

    @PostMapping("/users/{id}/credit/adjustments")
    public CreditChangeVO adjust(@RequestHeader("Authorization") String authorization, @PathVariable Long id, @Valid @RequestBody CreditAdjustmentRequest request) {
        return credit.adjust(auth.requirePermission(authorization, AdminPermission.USER_MANAGE), id, request.delta(), request.reason());
    }

    @PostMapping("/users/{id}/credit/events/{eventId}/reversal")
    public CreditChangeVO reverse(@RequestHeader("Authorization") String authorization, @PathVariable Long id, @PathVariable Long eventId, @Valid @RequestBody CreditReasonRequest request) {
        return credit.reverse(auth.requirePermission(authorization, AdminPermission.USER_MANAGE), id, eventId, request.reason());
    }

    @GetMapping("/risk-signals")
    public RiskSignalPageVO signals(@RequestHeader("Authorization") String authorization, @RequestParam(required = false) RiskSignalStatus status, @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        auth.requirePermission(authorization, AdminPermission.USER_VIEW);
        return credit.signals(status, page, size);
    }

    @PostMapping("/risk-signals/{id}/confirm")
    public CreditChangeVO confirm(@RequestHeader("Authorization") String authorization, @PathVariable Long id, @Valid @RequestBody CreditAdjustmentRequest request) {
        return credit.confirmSignal(auth.requirePermission(authorization, AdminPermission.USER_MANAGE), id, request.delta(), request.reason());
    }

    @PostMapping("/risk-signals/{id}/dismiss")
    public RiskSignalVO dismiss(@RequestHeader("Authorization") String authorization, @PathVariable Long id, @Valid @RequestBody CreditReasonRequest request) {
        return credit.dismissSignal(auth.requirePermission(authorization, AdminPermission.USER_MANAGE), id, request.reason());
    }
}
