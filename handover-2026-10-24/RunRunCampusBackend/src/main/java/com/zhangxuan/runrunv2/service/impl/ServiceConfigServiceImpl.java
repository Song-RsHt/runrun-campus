package com.zhangxuan.runrunv2.service.impl;

import com.zhangxuan.runrunv2.config.OrderJson;
import com.zhangxuan.runrunv2.dto.DeliveryZoneChangeRequest;
import com.zhangxuan.runrunv2.dto.OrderQuoteRequest;
import com.zhangxuan.runrunv2.dto.ServiceConfigChangeRequest;
import com.zhangxuan.runrunv2.dto.ServicePointChangeRequest;
import com.zhangxuan.runrunv2.enums.ServiceType;
import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.integration.idempotency.OrderIdempotency;
import com.zhangxuan.runrunv2.mapper.ServiceConfigMapper;
import com.zhangxuan.runrunv2.model.InputValidation;
import com.zhangxuan.runrunv2.model.OrderInput;
import com.zhangxuan.runrunv2.model.OrderPricing;
import com.zhangxuan.runrunv2.model.OrderSchedule;
import com.zhangxuan.runrunv2.service.IdentityService;
import com.zhangxuan.runrunv2.service.OriginalPaymentWriter;
import com.zhangxuan.runrunv2.service.ServiceConfigService;
import com.zhangxuan.runrunv2.vo.OrderQuoteVO;
import com.zhangxuan.runrunv2.vo.ServiceConfigVO;
import com.zhangxuan.runrunv2.vo.ServiceOptionVO;
import java.math.BigDecimal;
import java.sql.Time;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;





































































@Service
public class ServiceConfigServiceImpl implements ServiceConfigService {

    private final ServiceConfigMapper mapper;

    private final IdentityService identity;

    private final OrderIdempotency requests;

    private final OrderJson json;

    private final Clock clock;

    private final OriginalPaymentWriter payments;

    public ServiceConfigServiceImpl(ServiceConfigMapper mapper, IdentityService identity, OrderIdempotency requests, OrderJson json, Clock clock, OriginalPaymentWriter payments) {
        this.mapper = mapper;
        this.identity = identity;
        this.requests = requests;
        this.json = json;
        this.clock = clock;
        this.payments = payments;
    }

    // Shared configuration lock protects both the quote version and all constituent rows.
    // Updates lock the singleton first and commit all zone/point changes with one version.
    @Override
    public Map<String, Object> settings(boolean update) {
        return mapper.lockSettings(update);
    }

    @Transactional
    @Override
    public ServiceConfigVO config(String token) {
        var actor = identity.authenticate(token);
        Map<String, Object> settings = settings(false);
        return view(settings, "ADMIN".equals(actor.role()) && actor.adminEnabled());
    }

    private ServiceConfigVO view(Map<String, Object> settings, boolean includeDisabled) {
        List<ServiceOptionVO> services = Arrays.stream(ServiceType.values()).map(s -> new ServiceOptionVO(s.name(), s.name, s.baseCents, s.zoneSurcharge)).toList();
        var zones = mapper.findZones(includeDisabled);
        var points = mapper.findServicePoints(includeDisabled);
        return new ServiceConfigVO(services, zones, points, new OrderSchedule("Asia/Shanghai", "09:00", "23:00"), Boolean.TRUE.equals(settings.get("new_order_enabled")) && payments.available(), includeDisabled && settings.get("review_owner_id") != null ? settings.get("review_owner_id").toString() : null, OrderInput.number(settings, "config_version"), "1", OrderInput.number(settings, "version"));
    }

    @Transactional
    @Override
    public OrderQuoteVO quote(String token, OrderQuoteRequest request) {
        identity.requireRole(token, "USER", false);
        Map<String, Object> settings = settings(false);
        return new OrderQuoteVO(price(request, settings), OrderInput.number(settings, "config_version"));
    }

    @Override
    public OrderPricing price(OrderQuoteRequest request, Map<String, Object> settings) {
        OrderInput.validate(request);
        Map<String, Object> zone = zone(request.zoneCode());
        point(request.servicePointCode());
        // 基线§4：区域加价只适用于包裹；目的地文字不能推断区域或覆盖服务端金额。
        long surcharge = request.serviceType().zoneSurcharge ? OrderInput.number(zone, "package_surcharge_cents") : 0;
        return new OrderPricing(request.serviceType(), request.serviceType().baseCents, request.zoneCode(), surcharge, Math.addExact(request.serviceType().baseCents, surcharge), OrderInput.number(settings, "config_version"));
    }

    @Override
    public Map<String, Object> zone(String code) {
        return requireEnabled(mapper.findEnabledZone(code));
    }

    @Override
    public Map<String, Object> point(String code) {
        return requireEnabled(mapper.findEnabledServicePoint(code));
    }

    private Map<String, Object> requireEnabled(List<Map<String, Object>> rows) {
        if (rows.size() != 1)
            throw IdentityFailure.invalid();
        return rows.get(0);
    }

    @Override
    public void allowNewOrder(Map<String, Object> settings) {
        if (!Boolean.TRUE.equals(settings.get("new_order_enabled")))
            throw new IdentityFailure(503, "NEW_ORDER_DISABLED");
        requireOwner((Long) settings.get("review_owner_id"));
        LocalTime now = clock.instant().atZone(ZoneId.of(settings.get("business_timezone").toString())).toLocalTime();
        LocalTime open = ((Time) settings.get("business_open")).toLocalTime();
        LocalTime close = ((Time) settings.get("business_close")).toLocalTime();
        if (now.isBefore(open) || !now.isBefore(close))
            throw new IdentityFailure(409, "OUTSIDE_BUSINESS_HOURS");
    }

    @Override
    public Map<String, Object> policy(Map<String, Object> settings) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configVersion", OrderInput.number(settings, "config_version"));
        result.put("businessSchedule", new OrderSchedule("Asia/Shanghai", "09:00", "23:00"));
        for (String key : List.of("pending_payment_minutes", "delivery_confirm_minutes", "arrival_radius_m", "arrival_window_minutes", "arrival_subsidy_cents", "adjust_pay_timeout_minutes", "adjustment_review_remind_minutes", "adjustment_review_escalate_minutes", "overdue_debt_closure_days", "platform_share_percent")) {
            StringBuilder camel = new StringBuilder();
            boolean upper = false;
            for (char c : key.toCharArray()) {
                if (c == '_')
                    upper = true;
                else {
                    camel.append(upper ? Character.toUpperCase(c) : c);
                    upper = false;
                }
            }
            result.put(camel.toString(), OrderInput.number(settings, key));
        }
        result.put("courierSharePercent", 100 - OrderInput.number(settings, "platform_share_percent"));
        return result;
    }

    @Transactional
    @Override
    public ServiceConfigVO patch(String token, String key, ServiceConfigChangeRequest change) {
        // 契约M47：更换负责人和停用资格共用锁，避免并发留下失效接收人。
        identity.lock("review-owner-policy");
        var actor = identity.authorizeForWrite(token, "ADMIN", false);
        if (change == null || change.configVersion() == null || change.configVersion() < 0 || change.expectedVersion() == null || change.expectedVersion() < 0)
            throw IdentityFailure.invalid();
        InputValidation.text(change.reason(), 1000);
        ServiceConfigVO replay = requests.replay(actor.id(), "service-config:patch", key, change, ServiceConfigVO.class);
        if (replay != null)
            return replay;
        Map<String, Object> settings = settings(true);
        long version = OrderInput.number(settings, "config_version");
        if (version != change.configVersion())
            throw new IdentityFailure(409, "CONFIG_VERSION_CONFLICT");
        if (OrderInput.number(settings, "version") != change.expectedVersion())
            throw new IdentityFailure(409, "ORDER_STATE_CONFLICT");
        if (change.businessSchedule() != null && !new OrderSchedule("Asia/Shanghai", "09:00", "23:00").equals(change.businessSchedule()))
            throw IdentityFailure.invalid();
        Long owner = change.reviewOwnerId() == null ? (Long) settings.get("review_owner_id") : Long.valueOf(OrderInput.id(change.reviewOwnerId()));
        boolean enabled = change.newOrderEnabled() == null ? Boolean.TRUE.equals(settings.get("new_order_enabled")) : change.newOrderEnabled();
        if (owner != null || enabled)
            requireOwner(owner);
        if (change.zones() != null) {
            Set<String> codes = new HashSet<>();
            if (change.zones().size() > 100)
                throw IdentityFailure.invalid();
            for (DeliveryZoneChangeRequest zone : change.zones()) {
                if (zone == null)
                    throw IdentityFailure.invalid();
                OrderInput.code(zone.code());
                InputValidation.text(zone.name(), 100);
                if (!codes.add(zone.code()) || zone.enabled() == null || zone.packageSurchargeCents() == null || zone.packageSurchargeCents() < 0 || zone.packageSurchargeCents() > Long.MAX_VALUE - 800)
                    throw IdentityFailure.invalid();
                mapper.upsertZone(zone.code(), zone.name(), zone.packageSurchargeCents(), zone.enabled(), version + 1);
            }
        }
        if (change.servicePoints() != null) {
            Set<String> codes = new HashSet<>();
            if (change.servicePoints().size() > 100)
                throw IdentityFailure.invalid();
            for (ServicePointChangeRequest point : change.servicePoints()) {
                if (point == null)
                    throw IdentityFailure.invalid();
                OrderInput.code(point.code());
                InputValidation.text(point.name(), 100);
                if (!codes.add(point.code()) || point.enabled() == null || !"GCJ02".equals(point.coordinateSystem()) || (point.latitude() == null) != (point.longitude() == null))
                    throw IdentityFailure.invalid();
                if (point.latitude() != null && (point.latitude().scale() > 7 || point.longitude().scale() > 7 || point.latitude().abs().compareTo(BigDecimal.valueOf(90)) > 0 || point.longitude().abs().compareTo(BigDecimal.valueOf(180)) > 0))
                    throw IdentityFailure.invalid();
                mapper.upsertServicePoint(point.code(), point.name(), point.latitude(), point.longitude(), point.coordinateSystem(), point.enabled(), version + 1);
            }
        }
        mapper.updateSettings(enabled, owner);
        mapper.insertConfigChangeAudit(Long.valueOf(actor.id()), key, change.reason(), json.write(Map.of("oldConfigVersion", version, "newConfigVersion", version + 1, "changes", change)));
        ServiceConfigVO result = view(settings(true), true);
        requests.finish(actor.id(), "service-config:patch", key, "SERVICE_CONFIG", "1", result);
        return result;
    }

    private void requireOwner(Long id) {
        if (id == null || !Boolean.TRUE.equals(mapper.isEligibleReviewOwner(id)))
            throw new IdentityFailure(409, "REVIEW_OWNER_REQUIRED");
    }
}
