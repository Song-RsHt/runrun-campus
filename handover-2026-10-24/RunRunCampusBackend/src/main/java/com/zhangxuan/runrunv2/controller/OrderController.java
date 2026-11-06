package com.zhangxuan.runrunv2.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhangxuan.runrunv2.dto.CreateOrderRequest;
import com.zhangxuan.runrunv2.dto.OrderQuoteRequest;
import com.zhangxuan.runrunv2.dto.ServiceConfigChangeRequest;
import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.security.IdentityPermission;
import com.zhangxuan.runrunv2.security.IdentityWebConfiguration;
import com.zhangxuan.runrunv2.service.OrderService;
import com.zhangxuan.runrunv2.service.ServiceConfigService;
import com.zhangxuan.runrunv2.vo.CreatedOrderVO;
import com.zhangxuan.runrunv2.vo.OrderQuoteVO;
import com.zhangxuan.runrunv2.vo.OrderVO;
import com.zhangxuan.runrunv2.vo.ServiceConfigVO;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/** 订单创建与状态管理 HTTP 入口；绑定请求并委托业务服务，不直接访问数据库。 */
@RestController
@RequestMapping("/api/v2")
public class OrderController {

    private final ServiceConfigService config;
    private final OrderService orders;
    private final ObjectMapper json;

    private static final Set<String> CREATE_FIELDS =
            Set.of("serviceType", "zoneCode", "servicePointCode", "destination", "configVersion");

    private static final Set<String> QUOTE_FIELDS =
            Set.of("serviceType", "zoneCode", "servicePointCode", "destination");

    public OrderController(ServiceConfigService config, OrderService orders, ObjectMapper json) {
        this.config = config;
        this.orders = orders;
        this.json = json;
    }

    @GetMapping("/service-config")
    @IdentityPermission
    public ServiceConfigVO config(HttpServletRequest request) {
        return config.config(IdentityWebConfiguration.bearer(request));
    }

    @PostMapping("/quotes")
    @IdentityPermission(role = "USER")
    public OrderQuoteVO quote(HttpServletRequest request, @RequestBody String rawBody) {
        try {
            JsonNode body = json.readTree(rawBody);
            validateFields(body, QUOTE_FIELDS);
            return config.quote(
                    IdentityWebConfiguration.bearer(request),
                    json.treeToValue(body, OrderQuoteRequest.class));
        } catch (Exception e) {
            throw IdentityFailure.invalid();
        }
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @IdentityPermission(role = "USER")
    public CreatedOrderVO create(
            HttpServletRequest request,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody String rawBody) {
        final JsonNode body;
        try {
            body = json.readTree(rawBody);
        } catch (JsonProcessingException e) {
            throw IdentityFailure.invalid();
        }
        if (body == null || !body.isObject()) {
            throw IdentityFailure.invalid();
        }
        validateFields(body, CREATE_FIELDS);
        if (!body.has("configVersion") || !body.get("configVersion").isIntegralNumber()) {
            throw IdentityFailure.invalid();
        }
        try {
            return orders.create(
                    IdentityWebConfiguration.bearer(request),
                    key,
                    json.treeToValue(body, CreateOrderRequest.class));
        } catch (IllegalArgumentException | JsonProcessingException e) {
            throw IdentityFailure.invalid();
        }
    }

    private static void validateFields(JsonNode body, Set<String> allowed) {
        if (body == null || !body.isObject()) {
            throw IdentityFailure.invalid();
        }
        var fields = body.fieldNames();
        while (fields.hasNext()) {
            if (!allowed.contains(fields.next())) {
                throw IdentityFailure.invalid();
            }
        }
    }

    @GetMapping("/orders/{id}")
    @IdentityPermission(role = "USER")
    public OrderVO get(HttpServletRequest request, @PathVariable String id) {
        return orders.get(IdentityWebConfiguration.bearer(request), id);
    }

    @PatchMapping("/admin/service-config")
    @IdentityPermission(role = "ADMIN")
    public ServiceConfigVO patch(
            HttpServletRequest request,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody String rawBody) {
        try {
            JsonNode body = json.readTree(rawBody);
            if (body == null
                    || !body.isObject()
                    || (body.has("configVersion") && !body.get("configVersion").isIntegralNumber())
                    || (body.has("expectedVersion")
                            && !body.get("expectedVersion").isIntegralNumber())) {
                throw IdentityFailure.invalid();
            }
            for (JsonNode zone : body.withArray("zones")) {
                if (zone.has("packageSurchargeCents")
                        && !zone.get("packageSurchargeCents").isIntegralNumber()) {
                    throw IdentityFailure.invalid();
                }
            }
            return config.patch(
                    IdentityWebConfiguration.bearer(request),
                    key,
                    json.treeToValue(body, ServiceConfigChangeRequest.class));
        } catch (IdentityFailure e) {
            throw e;
        } catch (Exception e) {
            throw IdentityFailure.invalid();
        }
    }
}
