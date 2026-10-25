package com.zhangxuan.runruncampus.order;

import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRepository;
import com.zhangxuan.runruncampus.user.UserRole;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "app.proof-storage.local-directory=target/test-chat-proofs")
@ActiveProfiles("test")
@Testcontainers
@Transactional
class OrderChatPostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired OrderChatService chat;
    @Autowired OrderRepository orders;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    @Test
    void messagesPersistAndNewCourierCannotReadPreviousConversation() {
        AppUser customer = users.saveAndFlush(new AppUser(phone(), "hash", "用户", UserRole.CUSTOMER));
        AppUser firstCourier = users.saveAndFlush(new AppUser(phone(), "hash", "原骑手", UserRole.COURIER));
        AppUser nextCourier = users.saveAndFlush(new AppUser(phone(), "hash", "新骑手", UserRole.COURIER));
        CampusOrder order = new CampusOrder("CHAT-" + UUID.randomUUID().toString().substring(0, 12),
                customer, "商家", "1234", "东门", "宿舍", ServiceType.LOBBY);
        order.assignCourier(firstCourier);
        order = orders.saveAndFlush(order);

        chat.send(customer, order.getId(), "原骑手收到的消息");
        order.requestReassignment();
        order.assignCourier(nextCourier);
        orders.saveAndFlush(order);
        chat.send(customer, order.getId(), "新骑手收到的消息");

        assertEquals(2, jdbc.queryForObject("select count(*) from order_chat_messages where order_id = ?",
                Integer.class, order.getId()));
        assertEquals(0L, chat.unread(firstCourier).total());
        assertEquals(1L, chat.unread(nextCourier).total());
        assertEquals(2, chat.list(customer, order.getId()).size());
        assertEquals(1, chat.list(nextCourier, order.getId()).size());
        assertEquals("新骑手收到的消息", chat.list(nextCourier, order.getId()).get(0).content());
    }

    @Test
    void recipientUnreadCountClearsWhenConversationIsOpened() {
        AppUser customer = users.saveAndFlush(new AppUser(phone(), "hash", "用户", UserRole.CUSTOMER));
        AppUser courier = users.saveAndFlush(new AppUser(phone(), "hash", "骑手", UserRole.COURIER));
        CampusOrder order = new CampusOrder("CHAT-" + UUID.randomUUID().toString().substring(0, 12),
                customer, "商家", "1234", "东门", "宿舍", ServiceType.LOBBY);
        order.assignCourier(courier);
        order = orders.saveAndFlush(order);

        chat.send(customer, order.getId(), "请尽快送达");

        assertEquals(1L, chat.unread(courier).total());
        assertEquals(0L, chat.unread(customer).total());

        chat.list(courier, order.getId());

        assertEquals(0L, chat.unread(courier).total());
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from order_chat_messages where order_id = ? and read_at is not null",
                Integer.class, order.getId()));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void upgradeMarksExistingMessagesRead() {
        String schema = "chat_unread_upgrade";
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).target("33").load().migrate();
        jdbc.update("""
                insert into chat_unread_upgrade.app_users
                    (id, created_at, display_name, password_hash, phone, role)
                values (1, now(), '用户', 'hash', '19900000001', 'CUSTOMER'),
                       (2, now(), '骑手', 'hash', '19900000002', 'COURIER')
                """);
        jdbc.update("""
                insert into chat_unread_upgrade.campus_orders
                    (id, created_at, destination, fee_cents, merchant, order_no, pickup_code,
                     pickup_location, service_type, status, updated_at, customer_id, courier_id,
                     delivery_code, pickup_station, delivery_zone)
                values (1, now(), '宿舍', 200, '商家', 'CHAT-UPGRADE-1', '1234',
                        '东门', 'LOBBY', 'ACCEPTED', now(), 1, 2,
                        '123456', 'EAST_GATE_TAKEOUT', 'STANDARD')
                """);
        jdbc.update("""
                insert into chat_unread_upgrade.order_chat_messages
                    (order_id, sender_id, recipient_id, content, created_at)
                values (1, 1, 2, '迁移前消息', now())
                """);

        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).load().migrate();

        assertEquals(1, jdbc.queryForObject("""
                select count(*) from chat_unread_upgrade.order_chat_messages
                where read_at = created_at
                """, Integer.class));
    }

    private String phone() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 11);
    }
}
