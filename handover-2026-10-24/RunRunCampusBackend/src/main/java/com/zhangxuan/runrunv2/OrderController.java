package com.zhangxuan.runrunv2;

import static com.zhangxuan.runrunv2.OrderModels.*;
import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2")
public class OrderController {
    private final ServiceConfigService config;
    private final OrderService orders;
    private final ObjectMapper json;
    private static final Set<String> CREATE_FIELDS = Set.of("serviceType", "zoneCode", "servicePointCode", "destination", "configVersion");
    private static final Set<String> QUOTE_FIELDS = Set.of("serviceType", "zoneCode", "servicePointCode", "destination");
    public OrderController(ServiceConfigService config,OrderService orders,ObjectMapper json) { this.config=config; this.orders=orders; this.json=json; }
    @GetMapping("/service-config") @IdentityPermission
    public Config config(HttpServletRequest request) { return config.config(IdentityWebConfiguration.bearer(request)); }
    @PostMapping("/quotes") @IdentityPermission(role="USER")
    public Quote quote(HttpServletRequest request,@RequestBody String rawBody) {
        try {
            JsonNode body=json.readTree(rawBody); validateFields(body,QUOTE_FIELDS);
            return config.quote(IdentityWebConfiguration.bearer(request),json.treeToValue(body,QuoteRequest.class));
        } catch (Exception e) { throw IdentityFailure.invalid(); }
    }
    @PostMapping("/orders") @ResponseStatus(HttpStatus.CREATED) @IdentityPermission(role="USER")
    public Created create(HttpServletRequest request,@RequestHeader("Idempotency-Key") String key,@RequestBody String rawBody) {
        final JsonNode body;
        try { body = json.readTree(rawBody); } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw IdentityFailure.invalid(); }
        if (body == null || !body.isObject()) throw IdentityFailure.invalid();
        validateFields(body,CREATE_FIELDS);
        if (!body.has("configVersion") || !body.get("configVersion").isIntegralNumber()) throw IdentityFailure.invalid();
        try {
            return orders.create(IdentityWebConfiguration.bearer(request),key,json.treeToValue(body,CreateRequest.class));
        } catch (IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw IdentityFailure.invalid();
        }
    }
    private static void validateFields(JsonNode body,Set<String> allowed) {
        if (body == null || !body.isObject()) throw IdentityFailure.invalid();
        var fields=body.fieldNames(); while(fields.hasNext()) if(!allowed.contains(fields.next())) throw IdentityFailure.invalid();
    }
    @GetMapping("/orders/{id}") @IdentityPermission(role="USER")
    public Order get(HttpServletRequest request,@PathVariable String id) { return orders.get(IdentityWebConfiguration.bearer(request),id); }
    @PatchMapping("/admin/service-config") @IdentityPermission(role="ADMIN")
    public Config patch(HttpServletRequest request,@RequestHeader("Idempotency-Key") String key,@RequestBody String rawBody) {
        try {
            JsonNode body=json.readTree(rawBody); 
            if (body==null || !body.isObject() || (body.has("configVersion")&&!body.get("configVersion").isIntegralNumber())
                    || (body.has("expectedVersion")&&!body.get("expectedVersion").isIntegralNumber())) throw IdentityFailure.invalid();
            for (JsonNode zone: body.withArray("zones")) if (zone.has("packageSurchargeCents") && !zone.get("packageSurchargeCents").isIntegralNumber()) throw IdentityFailure.invalid();
            return config.patch(IdentityWebConfiguration.bearer(request),key,json.treeToValue(body,ConfigChange.class));
        } catch (IdentityFailure e) { throw e; }
        catch (Exception e) { throw IdentityFailure.invalid(); }
    }
}
