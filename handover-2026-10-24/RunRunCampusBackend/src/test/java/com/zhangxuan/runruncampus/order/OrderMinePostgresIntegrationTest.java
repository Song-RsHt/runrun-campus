package com.zhangxuan.runruncampus.order;

import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRepository;
import com.zhangxuan.runruncampus.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "app.proof-storage.local-directory=target/test-mine-proofs")
@ActiveProfiles("test")
@Testcontainers
@Transactional
class OrderMinePostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired OrderService service;
    @Autowired OrderRepository orders;
    @Autowired UserRepository users;

    @Test
    void historyPagesExcludeActiveAndOtherCustomersOrders() {
        AppUser customer = user();
        AppUser other = user();
        for (int i = 0; i < 4; i++) {
            order(customer, i == 0 ? OrderStatus.CANCELLED : OrderStatus.COMPLETED);
        }
        order(customer, OrderStatus.PENDING_ASSIGNMENT);
        order(other, OrderStatus.COMPLETED);

        var first = service.mineHistory(customer, 0, 2);
        var second = service.mineHistory(customer, 1, 2);

        assertEquals(4, first.totalElements());
        assertEquals(2, first.totalPages());
        assertEquals(2, first.items().size());
        assertEquals(2, second.items().size());
        assertNotEquals(first.items().get(0).id(), second.items().get(0).id());
        assertEquals(1, service.mineActive(customer).size());
    }

    @Test
    void historyFiltersByStatusAndInclusiveShanghaiDates() {
        AppUser customer = user();
        CampusOrder included = orderAt(customer, OrderStatus.COMPLETED,
                Instant.parse("2026-09-01T04:00:00Z"));
        orderAt(customer, OrderStatus.CANCELLED, Instant.parse("2026-09-02T04:00:00Z"));
        orderAt(customer, OrderStatus.COMPLETED, Instant.parse("2026-09-04T04:00:00Z"));

        var result = service.mineHistory(customer, 0, 20, OrderStatus.COMPLETED,
                LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-03"));

        assertEquals(1, result.totalElements());
        assertEquals(included.getId(), result.items().get(0).id());
        assertEquals(2, service.mineHistory(customer, 0, 20, OrderStatus.COMPLETED,
                null, null).totalElements());
    }

    private AppUser user() {
        return users.saveAndFlush(new AppUser(UUID.randomUUID().toString().substring(0, 11),
                "hash", "用户", UserRole.CUSTOMER));
    }

    private void order(AppUser customer, OrderStatus status) {
        orderAt(customer, status, null);
    }

    private CampusOrder orderAt(AppUser customer, OrderStatus status, Instant createdAt) {
        CampusOrder order = new CampusOrder("PAGE-" + UUID.randomUUID().toString().substring(0, 12),
                customer, "商家", "1234", "东门", "宿舍", ServiceType.LOBBY);
        order.updateStatus(status);
        if (createdAt != null) {
            ReflectionTestUtils.setField(order, "createdAt", createdAt);
        }
        return orders.saveAndFlush(order);
    }
}
