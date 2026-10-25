package com.zhangxuan.runruncampus.message;

import com.zhangxuan.runruncampus.order.CampusOrder;
import com.zhangxuan.runruncampus.order.OrderRepository;
import com.zhangxuan.runruncampus.order.ServiceType;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRepository;
import com.zhangxuan.runruncampus.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "app.push.enabled=true",
        "app.proof-storage.local-directory=target/test-push-proofs",
        "app.push.app-key=1",
        "app.push.ecs-role-name=test-role",
        "app.push.retry-initial-delay-millis=3600000"
})
@ActiveProfiles("test")
@Testcontainers
class PushDeliveryPostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired PushDeliveryRepository deliveries;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ApplicationEventPublisher events;
    @Autowired UserRepository users;
    @Autowired OrderRepository orders;
    @Autowired JdbcTemplate jdbc;

    @Test
    void committedTaskCanBeClaimedAndFinished() {
        AppUser customer = users.saveAndFlush(new AppUser("13900000001", "hash", "用户", UserRole.CUSTOMER));
        CampusOrder order = orders.saveAndFlush(new CampusOrder("PUSH-ORDER-1", customer,
                "商家", "1234", "东门", "宿舍", ServiceType.LOBBY));
        var event = new OrderPushRequested(customer.getId(), order.getId(), "订单更新", "请查看订单", "stable-token");
        new TransactionTemplate(transactions).executeWithoutResult(status -> events.publishEvent(event));

        var claimed = deliveries.claimNext();

        assertTrue(claimed.isPresent());
        assertEquals("stable-token", claimed.orElseThrow().notification().idempotentToken());
        deliveries.markSent(claimed.orElseThrow().id());
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from push_delivery_tasks
                where idempotent_token = 'stable-token' and status = 'SENT'
                """, Integer.class));

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            events.publishEvent(new OrderPushRequested(customer.getId(), order.getId(),
                    "不应提交", "事务回滚", "rolled-back-token"));
            status.setRollbackOnly();
        });
        assertEquals(0, deliveries.count(PushDeliveryStatus.PENDING));
    }

    @Test
    void destinationTaskIsIdempotentAndKeepsNullableOrder() {
        AppUser courier = users.saveAndFlush(new AppUser("13900000002", "hash", "骑手", UserRole.COURIER));
        var notification = new OrderPushRequested(courier.getId(), null, "骑手认证即将到期",
                "请更新认证材料", "certification-reminder-stable-token",
                PushDestination.COURIER_CERTIFICATION);

        deliveries.enqueue(notification);
        deliveries.enqueue(notification);

        assertEquals(1, jdbc.queryForObject("""
                select count(*) from push_delivery_tasks
                where idempotent_token = 'certification-reminder-stable-token'
                """, Integer.class));
        var claimed = deliveries.claimNext().orElseThrow();
        assertEquals(PushDestination.COURIER_CERTIFICATION, claimed.notification().destination());
        assertEquals(null, claimed.notification().orderId());
        deliveries.markSent(claimed.id());
    }
}
