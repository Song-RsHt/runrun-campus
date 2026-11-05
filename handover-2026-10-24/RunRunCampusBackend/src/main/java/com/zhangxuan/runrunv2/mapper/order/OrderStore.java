package com.zhangxuan.runrunv2.mapper.order;
import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.config.OrderJson;
import com.zhangxuan.runrunv2.model.order.OrderModels;
import com.zhangxuan.runrunv2.service.configuration.ServiceConfigService;

import static com.zhangxuan.runrunv2.model.order.OrderModels.*;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Shared order lock for later payment/fulfillment modules; never expose arbitrary state writes as HTTP. */
@Component
public class OrderStore {
    private final JdbcTemplate db;
    private final OrderJson json;
    public OrderStore(JdbcTemplate db, OrderJson json) { this.db=db; this.json=json; }
    public Order lock(String orderId, long expectedVersion) {
        if(!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Order lock requires a transaction");
        if(expectedVersion<0) throw IdentityFailure.invalid();
        Order order=read(orderId,true);
        if(order.version()!=expectedVersion) throw new IdentityFailure(409,"ORDER_STATE_CONFLICT");
        return order;
    }
    public Order read(String orderId, boolean locked) {
        var rows=db.query("SELECT * FROM runrun_v2.v2_orders WHERE id=?"+(locked?" FOR UPDATE":""), (r,n)->new Order(
                r.getString("id"),r.getString("order_no"),r.getInt("order_flow_version"),r.getString("user_id"),r.getString("status"),ServiceType.valueOf(r.getString("service_type")),
                r.getString("zone_id"),r.getString("service_point_id"),json.read(r.getString("address_snapshot"),Address.class),json.read(r.getString("pricing_snapshot"),Pricing.class),
                policy(r.getString("policy_snapshot")),r.getLong("original_fee_cents"),r.getLong("effective_fee_cents"),r.getObject("pending_payment_deadline",OffsetDateTime.class),
                r.getLong("version"),r.getObject("created_at",OffsetDateTime.class),r.getObject("updated_at",OffsetDateTime.class),r.getObject("delivered_at",OffsetDateTime.class),r.getObject("confirm_started_at",OffsetDateTime.class),r.getObject("confirm_deadline",OffsetDateTime.class)),ServiceConfigService.id(orderId));
        if(rows.isEmpty()) throw new IdentityFailure(404,"RESOURCE_NOT_FOUND");
        return rows.get(0);
    }
    @SuppressWarnings("unchecked")
    private Map<String,Object> policy(String value) { return json.read(value,Map.class); }
}
