package com.zhangxuan.runrunv2;

import static com.zhangxuan.runrunv2.OrderModels.*;
import java.time.*;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final IdentityService identity;
    private final ServiceConfigService config;
    private final OrderIdempotency requests;
    private final JdbcTemplate db;
    private final OrderJson json;
    private final OriginalPaymentWriter payments;
    private final OrderStore orders;
    private final Clock clock;
    public OrderService(IdentityService identity, ServiceConfigService config, OrderIdempotency requests, JdbcTemplate db,
                        OrderJson json, OriginalPaymentWriter payments, OrderStore orders, Clock clock) {
        this.identity=identity; this.config=config; this.requests=requests; this.db=db; this.json=json; this.payments=payments; this.orders=orders; this.clock=clock;
    }
    @Transactional
    public Created create(String token, String key, CreateRequest request) {
        var actor=identity.authorizeForWrite(token,"USER",false);
        if(request==null || request.configVersion()==null || request.configVersion()<0) throw IdentityFailure.invalid();
        ServiceConfigService.validate(request.quote());
        // Replay precedes mutable gates: a retry after closing time/config changes
        // still returns the original committed result and never extends its deadline.
        Created replay=requests.replay(actor.id(),"orders:create",key,request,Created.class);
        if(replay!=null) return replay;
        if(!payments.available()) throw new IdentityFailure(503,"NEW_ORDER_DISABLED");
        Map<String,Object> settings=config.settings(false);
        if(ServiceConfigService.number(settings,"config_version")!=request.configVersion()) throw new IdentityFailure(409,"CONFIG_VERSION_CONFLICT");
        config.allowNewOrder(settings);
        Pricing price=config.price(request.quote(),settings);
        Map<String,Object> zone=config.zone(request.zoneCode());
        Map<String,Object> point=config.point(request.servicePointCode());
        Address address=new Address(request.destination(),request.zoneCode(),zone.get("name").toString(),request.servicePointCode(),point.get("name").toString(),
                (java.math.BigDecimal)point.get("latitude"),(java.math.BigDecimal)point.get("longitude"),point.get("coordinate_system").toString());
        // PostgreSQL timestamps have microsecond precision; use the same instant
        // for the order, payment deadline and replay snapshot without DB rounding drift.
        OffsetDateTime created=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
        OffsetDateTime deadline=created.plusMinutes(ServiceConfigService.number(settings,"pending_payment_minutes"));
        String id=db.queryForObject("INSERT INTO runrun_v2.v2_orders(order_no,user_id,service_type,zone_id,service_point_id,address_snapshot,pricing_snapshot,policy_snapshot,original_fee_cents,effective_fee_cents,pending_payment_deadline,created_at,updated_at) VALUES(?,?,?,?,?,?::jsonb,?::jsonb,?::jsonb,?,?,?,?,?) RETURNING id::text",
                String.class,"V2"+UUID.randomUUID().toString().replace("-",""),Long.valueOf(actor.id()),request.serviceType().name(),zone.get("id"),point.get("id"),
                json.write(address),json.write(price),json.write(config.policy(settings)),price.totalCents(),price.totalCents(),deadline,created,created);
        OriginalPayment payment=payments.create(id,price.totalCents(),deadline);
        // Enforce the integration contract before committing any order or replay result.
        if(payment==null || payment.id()==null || !payment.id().matches("[1-9][0-9]*") || !id.equals(payment.orderId()) || !"ORIGINAL".equals(payment.purpose())
                || !"UNPAID".equals(payment.status()) || payment.amountCents()!=price.totalCents() || !deadline.toInstant().equals(payment.deadline().toInstant()) || payment.version()!=0) throw new IllegalStateException("M03 original payment contract violated");
        db.update("INSERT INTO runrun_v2.v2_order_events(order_id,actor_id,event_type,request_id,payload) VALUES(?,?,'ORDER_CREATED',?,?::jsonb)",Long.valueOf(id),Long.valueOf(actor.id()),key,json.write(Map.of("orderFlowVersion",2,"version",0,"originalFeeCents",price.totalCents())));
        Created result=new Created(orders.read(id,false),payment);
        requests.finish(actor.id(),"orders:create",key,"V2_ORDER",id,result);
        return result;
    }
    @Transactional(readOnly=true)
    public Order get(String token,String id) {
        var actor=identity.requireRole(token,"USER",false);
        Order order=orders.read(id,false);
        if(!actor.id().equals(order.userId())) throw new IdentityFailure(404,"RESOURCE_NOT_FOUND");
        return order;
    }
}
