package com.zhangxuan.runrunv2;

import java.time.Instant;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/admin/evidence-holds")
public class CourierEvidenceGovernanceController {
    private final CourierEvidenceGovernanceService service;
    public CourierEvidenceGovernanceController(CourierEvidenceGovernanceService service){this.service=service;}
    @IdentityPermission(role="ADMIN")
    @PostMapping
    public HoldResponse create(@RequestHeader("Authorization") String auth,@RequestBody HoldRequest body){
        return new HoldResponse(service.hold(token(auth),body.orderId(),body.certificationId(),body.reason(),body.reviewDueAt()));
    }
    @IdentityPermission(role="ADMIN")
    @PostMapping("/{id}/release")
    public void release(@RequestHeader("Authorization") String auth,@PathVariable long id){service.release(token(auth),id);}
    public record HoldRequest(Long orderId,Long certificationId,String reason,Instant reviewDueAt){}
    public record HoldResponse(long id){}
    private static String token(String h){if(h==null||!h.startsWith("Bearer "))throw IdentityFailure.auth();return h.substring(7);}
}
