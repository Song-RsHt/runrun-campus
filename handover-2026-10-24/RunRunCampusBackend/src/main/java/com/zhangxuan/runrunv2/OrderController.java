package com.zhangxuan.runrunv2;

import static com.zhangxuan.runrunv2.OrderModels.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2")
public class OrderController {
    private final ServiceConfigService config;
    private final OrderService orders;
    public OrderController(ServiceConfigService config,OrderService orders) { this.config=config; this.orders=orders; }
    @GetMapping("/service-config") @IdentityPermission
    public Config config(HttpServletRequest request) { return config.config(IdentityWebConfiguration.bearer(request)); }
    @PostMapping("/quotes") @IdentityPermission(role="USER")
    public Quote quote(HttpServletRequest request,@RequestBody QuoteRequest body) { return config.quote(IdentityWebConfiguration.bearer(request),body); }
    @PostMapping("/orders") @ResponseStatus(HttpStatus.CREATED) @IdentityPermission(role="USER")
    public Created create(HttpServletRequest request,@RequestHeader("Idempotency-Key") String key,@RequestBody CreateRequest body) { return orders.create(IdentityWebConfiguration.bearer(request),key,body); }
    @GetMapping("/orders/{id}") @IdentityPermission(role="USER")
    public Order get(HttpServletRequest request,@PathVariable String id) { return orders.get(IdentityWebConfiguration.bearer(request),id); }
    @PatchMapping("/admin/service-config") @IdentityPermission(role="ADMIN")
    public Config patch(HttpServletRequest request,@RequestHeader("Idempotency-Key") String key,@RequestBody ConfigChange body) { return config.patch(IdentityWebConfiguration.bearer(request),key,body); }
}
