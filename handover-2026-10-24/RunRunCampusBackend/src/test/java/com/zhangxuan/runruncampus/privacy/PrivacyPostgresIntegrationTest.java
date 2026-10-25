package com.zhangxuan.runruncampus.privacy;

import com.zhangxuan.runruncampus.address.AddressRepository;
import com.zhangxuan.runruncampus.address.UserAddress;
import com.zhangxuan.runruncampus.auth.AuthService;
import com.zhangxuan.runruncampus.auth.SessionClientType;
import com.zhangxuan.runruncampus.courier.CourierCertification;
import com.zhangxuan.runruncampus.courier.CourierCertificationRepository;
import com.zhangxuan.runruncampus.order.CampusOrder;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "app.proof-storage.local-directory=target/test-privacy-proofs")
@ActiveProfiles("test")
@Testcontainers
class PrivacyPostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired AuthService auth;
    @Autowired PrivacyService privacy;
    @Autowired UserRepository users;
    @Autowired OrderRepository orders;
    @Autowired AddressRepository addresses;
    @Autowired CourierCertificationRepository certifications;
    @Autowired JdbcTemplate jdbc;

    @Test
    void deletionPreservesHistoryButRemovesDirectDataAndFreesPhone() {
        AppUser customer = auth.register("13800138001", "password", "下单用户", UserRole.CUSTOMER,
                new AuthService.SessionMetadata(SessionClientType.ANDROID, "test")).getUser();
        AppUser courier = auth.register("13800138002", "password", "注销骑手", UserRole.COURIER,
                new AuthService.SessionMetadata(SessionClientType.ANDROID, "test")).getUser();
        addresses.save(new UserAddress(courier, "宿舍", "梅园1号楼", true));
        certifications.save(new CourierCertification(courier, "真实姓名", "20260001", "无锡学院"));
        CampusOrder order = new CampusOrder("PRIVACY-1", customer, "商家", "1234",
                "东门", "梅园1号楼", ServiceType.LOBBY);
        order.assignCourier(courier);
        order.updateStatus(OrderStatus.COMPLETED);
        Long orderId = orders.saveAndFlush(order).getId();

        privacy.deleteAccount(courier, "password", true);

        assertTrue(users.findByPhone("13800138002").isEmpty());
        assertEquals("已注销用户", jdbc.queryForObject(
                "select display_name from app_users where id = ?", String.class, courier.getId()));
        assertEquals(0L, jdbc.queryForObject(
                "select count(*) from user_sessions where user_id = ?", Long.class, courier.getId()));
        assertEquals(0L, jdbc.queryForObject(
                "select count(*) from user_addresses where user_id = ?", Long.class, courier.getId()));
        assertEquals(0L, jdbc.queryForObject(
                "select count(*) from courier_certifications where courier_id = ?", Long.class, courier.getId()));
        assertEquals(courier.getId(), jdbc.queryForObject(
                "select courier_id from campus_orders where id = ?", Long.class, orderId));

        AppUser replacement = auth.register("13800138002", "new-password", "新账号", UserRole.CUSTOMER,
                new AuthService.SessionMetadata(SessionClientType.ANDROID, "replacement")).getUser();
        assertEquals("13800138002", replacement.getPhone());
    }
}
