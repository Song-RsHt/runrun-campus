package com.zhangxuan.runruncampus.credit;

import com.zhangxuan.runruncampus.auth.AuthService;
import com.zhangxuan.runruncampus.auth.SessionClientType;
import com.zhangxuan.runruncampus.order.CampusOrder;
import com.zhangxuan.runruncampus.order.OrderEvent;
import com.zhangxuan.runruncampus.order.OrderEventRepository;
import com.zhangxuan.runruncampus.order.OrderRepository;
import com.zhangxuan.runruncampus.order.OrderStatus;
import com.zhangxuan.runruncampus.order.ServiceType;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRepository;
import com.zhangxuan.runruncampus.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "app.proof-storage.local-directory=target/test-credit-proofs")
@ActiveProfiles("test")
@Testcontainers
class CreditPostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired OrderRepository orders;
    @Autowired OrderEventRepository orderEvents;
    @Autowired RiskSignalMaintenance maintenance;
    @Autowired CreditService credit;
    @Autowired JdbcTemplate jdbc;

    @Test
    void frequentCancellationCreatesOneReviewSignalAndOnlyConfirmationChangesCredit() {
        AppUser administrator = users.saveAndFlush(
                new AppUser("13900000001", "hash", "风控管理员", UserRole.ADMIN));
        AppUser customer = auth.register("13900000002", "password", "取消用户", UserRole.CUSTOMER,
                new AuthService.SessionMetadata(SessionClientType.ANDROID, "credit-test")).getUser();
        assertEquals(100, jdbc.queryForObject(
                "select score from credit_profiles where user_id = ?", Integer.class, customer.getId()));

        for (int index = 1; index <= 3; index++) {
            CampusOrder order = new CampusOrder("CREDIT-" + index, customer, "商家", "1234",
                    "东门", "梅园1号楼", ServiceType.LOBBY);
            order.updateStatus(OrderStatus.CANCELLED);
            orders.saveAndFlush(order);
            orderEvents.saveAndFlush(new OrderEvent(order, customer, OrderStatus.CANCELLED));
        }

        maintenance.scan();
        maintenance.scan();

        var pending = credit.signals(RiskSignalStatus.PENDING, 0, 20);
        assertEquals(1, pending.totalElements());
        assertEquals(100, credit.profile(customer, 0, 20).score());

        credit.confirmSignal(administrator, pending.items().get(0).id(), -10, "人工核实存在重复无效下单");

        assertEquals(90, credit.profile(customer, 0, 20).score());
        assertEquals(0, credit.signals(RiskSignalStatus.PENDING, 0, 20).totalElements());
        assertEquals(1, credit.signals(RiskSignalStatus.CONFIRMED, 0, 20).totalElements());
    }
}
