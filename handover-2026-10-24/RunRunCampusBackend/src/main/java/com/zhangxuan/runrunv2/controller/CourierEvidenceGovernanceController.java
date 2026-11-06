package com.zhangxuan.runrunv2.controller;

import com.zhangxuan.runrunv2.dto.HoldRequest;
import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.security.IdentityPermission;
import com.zhangxuan.runrunv2.service.CourierEvidenceGovernanceService;
import com.zhangxuan.runrunv2.vo.EvidenceHoldVO;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;



























@RestController
@RequestMapping("/api/v2/admin/evidence-holds")
public class CourierEvidenceGovernanceController {

    private final CourierEvidenceGovernanceService service;

    public CourierEvidenceGovernanceController(CourierEvidenceGovernanceService service) {
        this.service = service;
    }

    @IdentityPermission(role = "ADMIN")
    @PostMapping
    public EvidenceHoldVO create(@RequestHeader("Authorization") String auth, @RequestHeader("Idempotency-Key") String key, @RequestBody HoldRequest body) {
        var result = service.hold(token(auth), body.orderId(), body.certificationId(), body.reason(), body.reviewDueAt(), key);
        return new EvidenceHoldVO(result.id());
    }

    @IdentityPermission(role = "ADMIN")
    @PostMapping("/{id}/release")
    public EvidenceHoldVO release(@RequestHeader("Authorization") String auth, @RequestHeader("Idempotency-Key") String key, @PathVariable long id) {
        return new EvidenceHoldVO(service.release(token(auth), id, key).id());
    }

    private static String token(String h) {
        if (h == null || !h.startsWith("Bearer "))
            throw IdentityFailure.auth();
        return h.substring(7);
    }
}
