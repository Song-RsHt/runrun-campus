package com.zhangxuan.runrunv2.integration.notification;

import com.zhangxuan.runrunv2.integration.wechat.WechatSubscriptionGateway;
import com.zhangxuan.runrunv2.mapper.NotificationOutboxMapper;
import com.zhangxuan.runrunv2.model.Delivery;
import com.zhangxuan.runrunv2.model.DeliveryResult;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;











/**
 * Claims and completes in short independent transactions; network I/O is outside both.
 */
@Service
public class NotificationOutboxWorker {

    private final NotificationOutboxMapper mapper;

    private final WechatSubscriptionGateway wechat;

    private final TransactionTemplate transactions;

    public NotificationOutboxWorker(NotificationOutboxMapper mapper, WechatSubscriptionGateway wechat, PlatformTransactionManager transactionManager) {
        this.mapper = mapper;
        this.wechat = wechat;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public List<Delivery> claim(int limit) {
        return transactions.execute(status -> mapper.claimOutboxDeliveries(Math.max(1, Math.min(limit, 100))));
    }

    public void complete(long id, boolean success, String error) {
        transactions.executeWithoutResult(status -> mapper.completeDelivery(id, success, sanitize(error)));
    }

    public void runOnce() {
        if (!m08Installed())
            return;
        transactions.executeWithoutResult(status -> mapper.runReviewTimers());
        for (Delivery delivery : claim(50)) {
            if ("IN_APP".equals(delivery.channel())) {
                complete(delivery.id(), true, null);
                continue;
            }
            try {
                DeliveryResult result = wechat.send(delivery.recipientId(), delivery.payload());
                complete(delivery.id(), result != null && result.sent(), result == null ? "WECHAT_SEND_FAILED" : result.error());
            } catch (Exception e) {
                complete(delivery.id(), false, "WECHAT_SEND_FAILED");
            }
        }
    }

    private boolean m08Installed() {
        try {
            return Boolean.TRUE.equals(mapper.isNotificationRuntimeInstalled());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank())
            return null;
        String clean = value.replaceAll("[^A-Za-z0-9_.:-]", "_");
        return clean.substring(0, Math.min(500, clean.length()));
    }
}
