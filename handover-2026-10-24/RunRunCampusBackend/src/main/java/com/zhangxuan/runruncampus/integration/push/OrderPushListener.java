package com.zhangxuan.runruncampus.integration.push;

import com.zhangxuan.runruncampus.model.OrderPushRequested;
import com.zhangxuan.runruncampus.repository.PushDeliveryRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(name = "app.push.enabled", havingValue = "true")
public class OrderPushListener {

    private final PushDeliveryRepository deliveries;

    public OrderPushListener(PushDeliveryRepository deliveries) {
        this.deliveries = deliveries;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOrderPushRequested(OrderPushRequested notification) {
        deliveries.enqueue(notification);
    }
}
