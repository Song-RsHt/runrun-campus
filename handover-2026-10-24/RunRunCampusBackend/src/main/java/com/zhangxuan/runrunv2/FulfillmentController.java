package com.zhangxuan.runrunv2;

import static com.zhangxuan.runrunv2.FulfillmentModels.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2")
public class FulfillmentController {
    private final FulfillmentService service;
    private final FulfillmentQueries queries;
    FulfillmentController(FulfillmentService service,FulfillmentQueries queries) { this.service=service; this.queries=queries; }
    @GetMapping("/courier/tasks") @IdentityPermission(role="COURIER")
    public Page<TaskItem> tasks(HttpServletRequest r,@RequestParam(required=false) String cursor,@RequestParam(required=false) Integer limit,@RequestParam(required=false) String status) { return queries.tasks(token(r),cursor,limit,status); }
    @GetMapping("/courier/order-pool") @IdentityPermission(role="COURIER",courierEligible=true)
    public Page<Order> pool(HttpServletRequest r,@RequestParam(required=false) String cursor,@RequestParam(required=false) Integer limit) { return queries.pool(token(r),cursor,limit); }
    @PostMapping("/orders/{id}/accept") @IdentityPermission(role="COURIER",courierEligible=true)
    public Result accept(HttpServletRequest r,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody Version body) { return service.accept(token(r),id,key,body); }
    @PostMapping("/tasks/{id}/respond") @IdentityPermission(role="COURIER")
    public Result respond(HttpServletRequest r,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody Respond body) { return service.respond(token(r),id,key,body); }
    @PostMapping("/orders/{id}/release") @IdentityPermission(role="COURIER",courierEligible=true)
    public Result release(HttpServletRequest r,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody Release body) { return service.release(token(r),id,key,body); }
    @PostMapping("/orders/{id}/uploads") @IdentityPermission(role="COURIER",courierEligible=true)
    public Ticket upload(HttpServletRequest r,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody Upload body) { return service.upload(token(r),id,key,body); }
    @PostMapping("/orders/{id}/proofs") @IdentityPermission(role="COURIER",courierEligible=true)
    public Proof proof(HttpServletRequest r,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody RegisterProof body) { return service.proof(token(r),id,key,body); }
    @PostMapping("/orders/{id}/locations") @IdentityPermission(role="COURIER",courierEligible=true)
    public Event location(HttpServletRequest r,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody Location body) { return service.location(token(r),id,key,body); }
    @PostMapping("/orders/{id}/pickup") @IdentityPermission(role="COURIER",courierEligible=true)
    public Result pickup(HttpServletRequest r,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody Pickup body) { return service.pickup(token(r),id,key,body); }
    @PostMapping("/orders/{id}/deliver") @IdentityPermission(role="COURIER",courierEligible=true)
    public Result deliver(HttpServletRequest r,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody Deliver body) { return service.deliver(token(r),id,key,body); }
    @PostMapping("/orders/{id}/confirm") @IdentityPermission(role="USER")
    public Order confirm(HttpServletRequest r,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody Version body) { return service.confirm(token(r),id,key,body); }
    private String token(HttpServletRequest r) { return IdentityWebConfiguration.bearer(r); }
}
