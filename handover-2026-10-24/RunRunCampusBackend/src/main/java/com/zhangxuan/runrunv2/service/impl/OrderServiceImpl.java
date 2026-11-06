package com.zhangxuan.runrunv2.service.impl;

import com.zhangxuan.runrunv2.config.OrderJson;
import com.zhangxuan.runrunv2.dto.CreateOrderRequest;
import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.integration.idempotency.OrderIdempotency;
import com.zhangxuan.runrunv2.mapper.OrderMapper;
import com.zhangxuan.runrunv2.mapper.OrderStore;
import com.zhangxuan.runrunv2.model.OrderAddress;
import com.zhangxuan.runrunv2.model.OrderInput;
import com.zhangxuan.runrunv2.model.OrderPricing;
import com.zhangxuan.runrunv2.service.IdentityService;
import com.zhangxuan.runrunv2.service.OrderService;
import com.zhangxuan.runrunv2.service.OriginalPaymentWriter;
import com.zhangxuan.runrunv2.service.ServiceConfigService;
import com.zhangxuan.runrunv2.vo.CreatedOrderVO;
import com.zhangxuan.runrunv2.vo.OrderVO;
import com.zhangxuan.runrunv2.vo.OriginalPaymentVO;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;























































/**
 * 编排 v2 报价、配置快照及订单创建。金额继续使用分及既有舍入规则，订单和原始付款共享调用方事务。 不添加 legacy 订单兼容逻辑。参见《订单与履约规则》《支付与退款规则》。
 */
@Service
public class OrderServiceImpl implements OrderService {

    private final IdentityService identity;

    private final ServiceConfigService config;

    private final OrderIdempotency requests;

    private final OrderMapper mapper;

    private final OrderJson json;

    private final OriginalPaymentWriter payments;

    private final OrderStore orders;

    private final Clock clock;

    public OrderServiceImpl(IdentityService identity, ServiceConfigService config, OrderIdempotency requests, OrderMapper mapper, OrderJson json, OriginalPaymentWriter payments, OrderStore orders, Clock clock) {
        this.identity = identity;
        this.config = config;
        this.requests = requests;
        this.mapper = mapper;
        this.json = json;
        this.payments = payments;
        this.orders = orders;
        this.clock = clock;
    }

    @Transactional
    @Override
    public CreatedOrderVO create(String token, String key, CreateOrderRequest request) {
        var actor = identity.authorizeForWrite(token, "USER", false);
        if (request == null || request.configVersion() == null || request.configVersion() < 0)
            throw IdentityFailure.invalid();
        OrderInput.validate(request.quote());
        // Replay precedes mutable gates: a retry after closing time/config changes
        // still returns the original committed result and never extends its deadline.
        CreatedOrderVO replay = requests.replay(actor.id(), "orders:create", key, request, CreatedOrderVO.class);
        if (replay != null)
            return replay;
        if (!payments.available())
            throw new IdentityFailure(503, "NEW_ORDER_DISABLED");
        Map<String, Object> settings = config.settings(false);
        if (OrderInput.number(settings, "config_version") != request.configVersion())
            throw new IdentityFailure(409, "CONFIG_VERSION_CONFLICT");
        config.allowNewOrder(settings);
        OrderPricing price = config.price(request.quote(), settings);
        Map<String, Object> zone = config.zone(request.zoneCode());
        Map<String, Object> point = config.point(request.servicePointCode());
        OrderAddress address = new OrderAddress(request.destination(), request.zoneCode(), zone.get("name").toString(), request.servicePointCode(), point.get("name").toString(), (BigDecimal) point.get("latitude"), (BigDecimal) point.get("longitude"), point.get("coordinate_system").toString());
        // PostgreSQL timestamps have microsecond precision; use the same instant
        // for the order, payment deadline and replay snapshot without DB rounding drift.
        OffsetDateTime created = clock.instant().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
        OffsetDateTime deadline = created.plusMinutes(OrderInput.number(settings, "pending_payment_minutes"));
        String id = mapper.insertOrder("V2" + UUID.randomUUID().toString().replace("-", ""), Long.valueOf(actor.id()), request.serviceType().name(), ((Number) zone.get("id")).longValue(), ((Number) point.get("id")).longValue(), json.write(address), json.write(price), json.write(config.policy(settings)), price.totalCents(), price.totalCents(), deadline, created, created);
        OriginalPaymentVO payment = payments.create(id, price.totalCents(), deadline);
        // Enforce the integration contract before committing any order or replay result.
        if (payment == null || payment.id() == null || !payment.id().matches("[1-9][0-9]*") || !id.equals(payment.orderId()) || !"ORIGINAL".equals(payment.purpose()) || !"UNPAID".equals(payment.status()) || payment.amountCents() != price.totalCents() || !deadline.toInstant().equals(payment.deadline().toInstant()) || payment.version() != 0)
            throw new IllegalStateException("M03 original payment contract violated");
        mapper.insertOrderCreatedEvent(Long.valueOf(id), Long.valueOf(actor.id()), key, json.write(Map.of("orderFlowVersion", 2, "version", 0, "originalFeeCents", price.totalCents())));
        CreatedOrderVO result = new CreatedOrderVO(orders.read(id, false), payment);
        requests.finish(actor.id(), "orders:create", key, "V2_ORDER", id, result);
        return result;
    }

    @Transactional(readOnly = true)
    @Override
    public OrderVO get(String token, String id) {
        var actor = identity.requireRole(token, "USER", false);
        OrderVO order = orders.read(id, false);
        if (!actor.id().equals(order.userId()))
            throw new IdentityFailure(404, "RESOURCE_NOT_FOUND");
        return order;
    }
}
