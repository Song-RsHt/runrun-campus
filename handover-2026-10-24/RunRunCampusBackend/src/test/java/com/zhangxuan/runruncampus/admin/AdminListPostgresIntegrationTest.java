package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.order.CampusOrder;
import com.zhangxuan.runruncampus.order.OrderEvent;
import com.zhangxuan.runruncampus.order.OrderEventRepository;
import com.zhangxuan.runruncampus.order.OrderProof;
import com.zhangxuan.runruncampus.order.OrderProofRepository;
import com.zhangxuan.runruncampus.order.OrderProofType;
import com.zhangxuan.runruncampus.order.OrderRepository;
import com.zhangxuan.runruncampus.order.OrderStatus;
import com.zhangxuan.runruncampus.order.ServiceType;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRepository;
import com.zhangxuan.runruncampus.user.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "app.proof-storage.local-directory=target/test-order-proofs")
@ActiveProfiles("test")
@Testcontainers
class AdminListPostgresIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private AdminOrderService orderService;
    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    private AdminOrderLifecycleService lifecycleService;
    @Autowired
    private AdminAuditLogRepository auditLogRepository;
    @Autowired
    private AdminCourierService courierService;
    @Autowired
    private AdminUserService userService;
    @Autowired
    private AdminIssueService issueService;
    @Autowired
    private AdminFeedbackService feedbackService;
    @Autowired
    private AdminAuditService auditService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderEventRepository orderEventRepository;
    @Autowired
    private OrderProofRepository orderProofRepository;

    @Test
    void listsAndExportsWithoutOptionalQuery() {
        assertDoesNotThrow(() -> orderService.list(null, null, false, 0, 10));
        assertDoesNotThrow(() -> orderService.exportCsv(null, null, false));
        assertDoesNotThrow(() -> courierService.list(null, null, 0, 10));
        assertDoesNotThrow(() -> userService.list(null, null, 0, 10));
        assertDoesNotThrow(() -> issueService.list(null, null, null, 0, 10));
        assertDoesNotThrow(() -> feedbackService.list(null, null, null, 0, 10));
        assertDoesNotThrow(() -> auditService.list(null, null, 0, 10));
    }

    @Test
    void keepsKeywordSearchQueriesExecutable() {
        assertDoesNotThrow(() -> orderService.list("不存在", null, false, 0, 10));
        assertDoesNotThrow(() -> courierService.list("不存在", null, 0, 10));
        assertDoesNotThrow(() -> userService.list("不存在", null, 0, 10));
        assertDoesNotThrow(() -> issueService.list("不存在", null, null, 0, 10));
        assertDoesNotThrow(() -> feedbackService.list("不存在", null, null, 0, 10));
        assertDoesNotThrow(() -> auditService.list("不存在", null, 0, 10));
    }

    @Test
    @Transactional
    void reportsOnlyCompletedOrdersForEachCourier() {
        AppUser customer = userRepository.save(
                new AppUser("13800009001", "hash", "统计测试用户", UserRole.CUSTOMER));
        AppUser courier = userRepository.save(
                new AppUser("13800009002", "hash", "统计测试骑手", UserRole.COURIER));

        CampusOrder firstCompleted = order("COUNT-TEST-1", customer, courier, ServiceType.LOBBY);
        firstCompleted.updateStatus(OrderStatus.COMPLETED);
        CampusOrder secondCompleted = order("COUNT-TEST-2", customer, courier, ServiceType.PACKAGE_SMALL);
        secondCompleted.updateStatus(OrderStatus.COMPLETED);
        CampusOrder thirdCompleted = order("COUNT-TEST-3", customer, courier, ServiceType.PACKAGE_EXTRA_LARGE);
        thirdCompleted.updateStatus(OrderStatus.COMPLETED);
        CampusOrder accepted = order("COUNT-TEST-4", customer, courier, ServiceType.PACKAGE_MEDIUM_LARGE);
        orderRepository.saveAllAndFlush(java.util.List.of(firstCompleted, secondCompleted, thirdCompleted, accepted));
        orderEventRepository.saveAndFlush(new OrderEvent(secondCompleted, courier, OrderStatus.COMPLETED));
        orderEventRepository.saveAndFlush(new OrderEvent(thirdCompleted, courier, OrderStatus.COMPLETED));

        AdminCourierController.AdminCourierPageResponse response =
                courierService.list("13800009002", null, 0, 10);

        assertEquals(1, response.items().size());
        assertEquals(3, response.items().get(0).completedOrderCount());
        assertEquals(2, response.items().get(0).completedPackageOrderCount());

        AdminCourierController.AdminCourierPerformanceResponse performance =
                courierService.performance(courier.getId(), 0, 10);
        assertEquals(2, performance.todayCompletedPackageOrders());
        assertEquals(2, performance.lastSevenDaysCompletedPackageOrders());
        assertEquals(2, performance.currentMonthCompletedPackageOrders());
        assertEquals(2, performance.totalCompletedPackageOrders());
        assertEquals(1, performance.smallCompletedPackageOrders());
        assertEquals(0, performance.mediumCompletedPackageOrders());
        assertEquals(0, performance.mediumLargeCompletedPackageOrders());
        assertEquals(0, performance.largeCompletedPackageOrders());
        assertEquals(1, performance.specialLargeCompletedPackageOrders());
        assertEquals(0, performance.extraLargeCompletedPackageOrders());
        assertEquals(3, performance.orders().totalElements());
        assertEquals(Set.of("COUNT-TEST-2", "COUNT-TEST-3", "COUNT-TEST-4"),
                performance.orders().items().stream()
                        .map(AdminCourierController.AdminCourierOrderItem::orderNo)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    @Transactional
    void archivedOrderIsHiddenButDetailAndAuditRemain() {
        AppUser admin = userRepository.save(new AppUser("13800009101", "hash", "归档管理员", UserRole.ADMIN));
        AppUser customer = userRepository.save(new AppUser("13800009102", "hash", "归档用户", UserRole.CUSTOMER));
        CampusOrder order = new CampusOrder("ARCHIVE-TEST-1", customer, "测试商家", "1234", "东门", "1号楼",
                ServiceType.LOBBY);
        order.updateStatus(OrderStatus.CANCELLED);
        orderRepository.saveAndFlush(order);

        lifecycleService.archive(admin, order.getId(), "已结束测试订单归档");
        orderRepository.flush();
        entityManager.clear();

        assertTrue(orderService.list("ARCHIVE-TEST-1", null, false, 0, 10).items().isEmpty());
        assertEquals(1, orderService.list("ARCHIVE-TEST-1", null, true, 0, 10).items().size());
        assertNotNull(orderService.detail(order.getId()));
        assertTrue(auditLogRepository.findAll().stream().anyMatch(log ->
                log.getTargetId().equals(order.getId()) && log.getAction() == AdminAuditAction.ORDER_ARCHIVED));

        lifecycleService.unarchive(admin, order.getId());
        orderRepository.flush();
        entityManager.clear();
        assertEquals(1, orderService.list("ARCHIVE-TEST-1", null, false, 0, 10).items().size());
        assertTrue(auditLogRepository.findAll().stream().anyMatch(log ->
                log.getTargetId().equals(order.getId()) && log.getAction() == AdminAuditAction.ORDER_UNARCHIVED));
    }

    @Test
    @Transactional
    void forceCompletionPersistsStatusEventAndAudit() {
        AppUser admin = userRepository.save(new AppUser("13800009111", "hash", "完成管理员", UserRole.ADMIN));
        AppUser customer = userRepository.save(new AppUser("13800009112", "hash", "完成用户", UserRole.CUSTOMER));
        AppUser courier = userRepository.save(new AppUser("13800009113", "hash", "完成骑手", UserRole.COURIER));
        CampusOrder order = order("COMPLETE-TEST-1", customer, courier, ServiceType.LOBBY);
        order.updateStatus(OrderStatus.PICKED_UP);
        orderRepository.saveAndFlush(order);
        orderProofRepository.saveAndFlush(new OrderProof(order, courier, OrderProofType.DELIVERY,
                "complete-test-proof.jpg", "proof.jpg", "image/jpeg", 16));

        lifecycleService.forceComplete(admin, order.getId(), "已核对送达照片和收款记录");
        orderRepository.flush();
        entityManager.clear();

        assertEquals(OrderStatus.COMPLETED, orderRepository.findById(order.getId()).orElseThrow().getStatus());
        assertTrue(orderEventRepository.findByOrderOrderByCreatedAtAsc(order).stream().anyMatch(event ->
                event.getStatus() == OrderStatus.COMPLETED && event.getActor().getId().equals(admin.getId())));
        assertTrue(auditLogRepository.findAll().stream().anyMatch(log ->
                log.getTargetId().equals(order.getId()) && log.getAction() == AdminAuditAction.ORDER_FORCE_COMPLETED));
    }

    private CampusOrder order(String orderNo, AppUser customer, AppUser courier, ServiceType serviceType) {
        CampusOrder order = new CampusOrder(
                orderNo, customer, "统计测试商家", "1234", "东门", "1号楼", serviceType);
        order.assignCourier(courier);
        return order;
    }
}
