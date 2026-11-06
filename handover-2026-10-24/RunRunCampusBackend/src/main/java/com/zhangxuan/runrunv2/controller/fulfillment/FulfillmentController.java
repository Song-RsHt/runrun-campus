package com.zhangxuan.runrunv2.controller.fulfillment;

import com.zhangxuan.runrunv2.dto.fulfillment.FulfillmentDeliverRequest;
import com.zhangxuan.runrunv2.dto.fulfillment.FulfillmentLocationRequest;
import com.zhangxuan.runrunv2.dto.fulfillment.FulfillmentPickupRequest;
import com.zhangxuan.runrunv2.dto.fulfillment.FulfillmentReleaseRequest;
import com.zhangxuan.runrunv2.dto.fulfillment.FulfillmentRespondRequest;
import com.zhangxuan.runrunv2.dto.fulfillment.FulfillmentUploadRequest;
import com.zhangxuan.runrunv2.dto.fulfillment.FulfillmentVersionRequest;
import com.zhangxuan.runrunv2.dto.fulfillment.RegisterFulfillmentProofRequest;
import com.zhangxuan.runrunv2.security.IdentityPermission;
import com.zhangxuan.runrunv2.security.IdentityWebConfiguration;
import com.zhangxuan.runrunv2.service.fulfillment.FulfillmentQueryService;
import com.zhangxuan.runrunv2.service.fulfillment.FulfillmentService;
import com.zhangxuan.runrunv2.vo.fulfillment.CourierTaskVO;
import com.zhangxuan.runrunv2.vo.fulfillment.CursorPageVO;
import com.zhangxuan.runrunv2.vo.fulfillment.FulfillmentOrderVO;
import com.zhangxuan.runrunv2.vo.fulfillment.FulfillmentResultVO;
import com.zhangxuan.runrunv2.vo.fulfillment.LocationEventVO;
import com.zhangxuan.runrunv2.vo.fulfillment.OrderProofVO;
import com.zhangxuan.runrunv2.vo.fulfillment.UploadTicketVO;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 订单履约 HTTP 入口；绑定请求并委托业务服务，不直接访问数据库。 */
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
    public CursorPageVO<CourierTaskVO> tasks(
            HttpServletRequest r,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String status) {
        return queries.tasks(token(r), cursor, limit, status);
    }

    @GetMapping("/courier/order-pool")
    @IdentityPermission(role = "COURIER", courierEligible = true)
    public CursorPageVO<FulfillmentOrderVO> pool(
            HttpServletRequest r,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return queries.pool(token(r), cursor, limit);
    }

    @PostMapping("/orders/{id}/accept")
    @IdentityPermission(role = "COURIER", courierEligible = true)
    public FulfillmentResultVO accept(
            HttpServletRequest r,
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody FulfillmentVersionRequest body) {
        return service.accept(token(r), id, key, body);
    }

    @PostMapping("/tasks/{id}/respond")
    @IdentityPermission(role = "COURIER")
    public FulfillmentResultVO respond(
            HttpServletRequest r,
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody FulfillmentRespondRequest body) {
        return service.respond(token(r), id, key, body);
    }

    @PostMapping("/orders/{id}/release")
    @IdentityPermission(role = "COURIER", courierEligible = true)
    public FulfillmentResultVO release(
            HttpServletRequest r,
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody FulfillmentReleaseRequest body) {
        return service.release(token(r), id, key, body);
    }

    @PostMapping("/orders/{id}/uploads")
    @IdentityPermission(role = "COURIER", courierEligible = true)
    public UploadTicketVO upload(
            HttpServletRequest r,
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody FulfillmentUploadRequest body) {
        return service.upload(token(r), id, key, body);
    }

    @PostMapping("/orders/{id}/proofs")
    @IdentityPermission(role = "COURIER", courierEligible = true)
    public OrderProofVO proof(
            HttpServletRequest r,
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody RegisterFulfillmentProofRequest body) {
        return service.proof(token(r), id, key, body);
    }

    @PostMapping("/orders/{id}/locations")
    @IdentityPermission(role = "COURIER", courierEligible = true)
    public LocationEventVO location(
            HttpServletRequest r,
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody FulfillmentLocationRequest body) {
        return service.location(token(r), id, key, body);
    }

    @PostMapping("/orders/{id}/pickup")
    @IdentityPermission(role = "COURIER", courierEligible = true)
    public FulfillmentResultVO pickup(
            HttpServletRequest r,
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody FulfillmentPickupRequest body) {
        return service.pickup(token(r), id, key, body);
    }

    @PostMapping("/orders/{id}/deliver")
    @IdentityPermission(role = "COURIER", courierEligible = true)
    public FulfillmentResultVO deliver(
            HttpServletRequest r,
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody FulfillmentDeliverRequest body) {
        return service.deliver(token(r), id, key, body);
    }

    @PostMapping("/orders/{id}/confirm")
    @IdentityPermission(role = "USER")
    public FulfillmentOrderVO confirm(
            HttpServletRequest r,
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody FulfillmentVersionRequest body) {
        return service.confirm(token(r), id, key, body);
    }

    private String token(HttpServletRequest r) {
        return IdentityWebConfiguration.bearer(r);
    }
}
