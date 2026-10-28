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
    public HoldResponse create(@RequestHeader("Authorization") String auth,@RequestHeader("Idempotency-Key") String key,@RequestBody HoldRequest body){
        var result=service.hold(token(auth),body.orderId(),body.certificationId(),body.reason(),body.reviewDueAt(),key);
        return new HoldResponse(result.id());
    }
    @IdentityPermission(role="ADMIN")
    @PostMapping("/{id}/release")
    public HoldResponse release(@RequestHeader("Authorization") String auth,@RequestHeader("Idempotency-Key") String key,@PathVariable long id){return new HoldResponse(service.release(token(auth),id,key).id());}
    public record HoldRequest(Long orderId,Long certificationId,String reason,Instant reviewDueAt){}
    public record HoldResponse(long id){}
    private static String token(String h){if(h==null||!h.startsWith("Bearer "))throw IdentityFailure.auth();return h.substring(7);}
}
