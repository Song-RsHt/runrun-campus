package com.zhangxuan.runrunv2.controller;

import com.zhangxuan.runrunv2.dto.Deliver;
import com.zhangxuan.runrunv2.dto.Location;
import com.zhangxuan.runrunv2.dto.Pickup;
import com.zhangxuan.runrunv2.dto.RegisterProof;
import com.zhangxuan.runrunv2.dto.Release;
import com.zhangxuan.runrunv2.dto.Respond;
import com.zhangxuan.runrunv2.dto.Upload;
import com.zhangxuan.runrunv2.dto.Version;
import com.zhangxuan.runrunv2.security.IdentityPermission;
import com.zhangxuan.runrunv2.security.IdentityWebConfiguration;
import com.zhangxuan.runrunv2.service.FulfillmentQueryService;
import com.zhangxuan.runrunv2.service.FulfillmentService;
import com.zhangxuan.runrunv2.vo.CourierTaskVO;
import com.zhangxuan.runrunv2.vo.CursorPageVO;
import com.zhangxuan.runrunv2.vo.FulfillmentOrderVO;
import com.zhangxuan.runrunv2.vo.FulfillmentResultVO;
import com.zhangxuan.runrunv2.vo.LocationEventVO;
import com.zhangxuan.runrunv2.vo.OrderProofVO;
import com.zhangxuan.runrunv2.vo.UploadTicketVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2")
public class FulfillmentController {

    private final FulfillmentService service;

    private final FulfillmentQueryService queries;

    public FulfillmentController(FulfillmentService service, FulfillmentQueryService queries) {
        this.service = service;
        this.queries = queries;
    }

    @GetMapping("/courier/tasks")
    @IdentityPermission(role = "COURIER")
    public CursorPageVO<CourierTaskVO> tasks(HttpServletRequest r, @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit, @RequestParam(required = false) String status) {
        return queries.tasks(token(r), cursor, limit, status);
    }

    @GetMapping("/courier/order-pool")
    @IdentityPermission(role = "COURIER", courierEligible = true)
    public CursorPageVO<FulfillmentOrderVO> pool(HttpServletRequest r, @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
        return queries.pool(token(r), cursor, limit);
    }

    @PostMapping("/orders/{id}/accept")
    @IdentityPermission(role = "COURIER", courierEligible = true)
    public FulfillmentResultVO accept(HttpServletRequest r, @PathVariable String id, @RequestHeader("Idempotency-Key") String key, @RequestBody Version body) {
        return service.accept(token(r), id, key, body);
    }

    @PostMapping("/tasks/{id}/respond")
    @IdentityPermission(role = "COURIER")
    public FulfillmentResultVO respond(HttpServletRequest r, @PathVariable String id, @RequestHeader("Idempotency-Key") String key, @RequestBody Respond body) {
        return service.respond(token(r), id, key, body);
    }

    @PostMapping("/orders/{id}/release")
    @IdentityPermission(role = "COURIER", courierEligible = true)
    public FulfillmentResultVO release(HttpServletRequest r, @PathVariable String id, @RequestHeader("Idempotency-Key") String key, @RequestBody Release body) {
        return service.release(token(r), id, key, body);
    }

    @PostMapping("/orders/{id}/uploads")
    @IdentityPermission(role = "COURIER", courierEligible = true)
    public UploadTicketVO upload(HttpServletRequest r, @PathVariable String id, @RequestHeader("Idempotency-Key") String key, @RequestBody Upload body) {
        return service.upload(token(r), id, key, body);
    }

    @PostMapping("/orders/{id}/proofs")
    @IdentityPermission(role = "COURIER", courierEligible = true)
    public OrderProofVO proof(HttpServletRequest r, @PathVariable String id, @RequestHeader("Idempotency-Key") String key, @RequestBody RegisterProof body) {
        return service.proof(token(r), id, key, body);
    }

    @PostMapping("/orders/{id}/locations")
    @IdentityPermission(role = "COURIER", courierEligible = true)
    public LocationEventVO location(HttpServletRequest r, @PathVariable String id, @RequestHeader("Idempotency-Key") String key, @RequestBody Location body) {
        return service.location(token(r), id, key, body);
    }

    @PostMapping("/orders/{id}/pickup")
    @IdentityPermission(role = "COURIER", courierEligible = true)
    public FulfillmentResultVO pickup(HttpServletRequest r, @PathVariable String id, @RequestHeader("Idempotency-Key") String key, @RequestBody Pickup body) {
        return service.pickup(token(r), id, key, body);
    }

    @PostMapping("/orders/{id}/deliver")
    @IdentityPermission(role = "COURIER", courierEligible = true)
    public FulfillmentResultVO deliver(HttpServletRequest r, @PathVariable String id, @RequestHeader("Idempotency-Key") String key, @RequestBody Deliver body) {
        return service.deliver(token(r), id, key, body);
    }

    @PostMapping("/orders/{id}/confirm")
    @IdentityPermission(role = "USER")
    public FulfillmentOrderVO confirm(HttpServletRequest r, @PathVariable String id, @RequestHeader("Idempotency-Key") String key, @RequestBody Version body) {
        return service.confirm(token(r), id, key, body);
    }

    private String token(HttpServletRequest r) {
        return IdentityWebConfiguration.bearer(r);
    }
}
