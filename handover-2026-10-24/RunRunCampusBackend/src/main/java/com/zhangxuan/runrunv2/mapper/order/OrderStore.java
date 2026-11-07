package com.zhangxuan.runrunv2.mapper.order;

import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.model.resource.ResourceIds;
import com.zhangxuan.runrunv2.vo.order.OrderVO;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 订单共享锁入口，供支付和履约协作使用；不能向 HTTP 暴露任意状态写入。 */
@Component
public class OrderStore {

    private final OrderReadMapper mapper;

    public OrderStore(OrderReadMapper mapper) {
        this.mapper = mapper;
    }

    public OrderVO lock(String orderId, long expectedVersion) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Order lock requires a transaction");
        }
        if (expectedVersion < 0) {
            throw IdentityFailure.invalid();
        }
        OrderVO order = read(orderId, true);
        if (order.version() != expectedVersion) {
            throw new IdentityFailure(409, "ORDER_STATE_CONFLICT");
        }
        return order;
    }

    /** 读取并锁定 runrun_v2.v2_orders 的匹配行；锁持有至调用方事务结束。 */
    public OrderVO read(String orderId, boolean locked) {
        var rows = mapper.findOrder(ResourceIds.parse(orderId), locked);
        if (rows.isEmpty()) {
            throw new IdentityFailure(404, "RESOURCE_NOT_FOUND");
        }
        return rows.get(0);
    }
}
