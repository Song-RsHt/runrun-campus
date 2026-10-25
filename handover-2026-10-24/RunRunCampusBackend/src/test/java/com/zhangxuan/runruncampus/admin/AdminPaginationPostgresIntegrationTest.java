package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.courier.CourierCertification;
import com.zhangxuan.runruncampus.courier.CourierCertificationStatus;
import com.zhangxuan.runruncampus.feedback.FeedbackHandlingStatus;
import com.zhangxuan.runruncampus.feedback.FeedbackType;
import com.zhangxuan.runruncampus.feedback.UserFeedback;
import com.zhangxuan.runruncampus.order.CampusOrder;
import com.zhangxuan.runruncampus.order.OrderIssue;
import com.zhangxuan.runruncampus.order.OrderIssueHandlingStatus;
import com.zhangxuan.runruncampus.order.OrderIssueType;
import com.zhangxuan.runruncampus.order.OrderStatus;
import com.zhangxuan.runruncampus.order.ServiceType;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "app.proof-storage.local-directory=target/test-order-proofs")
@ActiveProfiles("test")
@Testcontainers
@Transactional
class AdminPaginationPostgresIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private EntityManager entities;
    @Autowired private AdminOrderService orders;
    @Autowired private AdminUserService users;
    @Autowired private AdminCourierService couriers;
    @Autowired private AdminIssueService issues;
    @Autowired private AdminFeedbackService feedback;
    @Autowired private AdminAuditService audit;
    private final Map<String, List<Long>> ids = new HashMap<>();
    private Long courierId;

    @BeforeEach
    void seedTimestampTiesAndFilterDecoys() {
        AppUser customer = new AppUser("19903001000", "hash", "Customer", UserRole.CUSTOMER);
        AppUser administrator = new AppUser("19903001001", "hash", "Administrator", UserRole.ADMIN);
        entities.persist(customer);
        entities.persist(administrator);
        List<AppUser> people = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            // Six matches, a keyword mismatch, an enum mismatch and a handling-status mismatch.
            String text = (i == 6 ? "Outside" : "Match") + i;
            // Three equal timestamps span a page boundary when the page size is two.
            Instant time = Instant.parse("2026-01-02T12:00:00Z").minusSeconds(i / 3);
            AppUser person = new AppUser("1990300110" + i, "hash", text,
                    i < 7 ? UserRole.COURIER : UserRole.CUSTOMER);
            persistAt("users", person, time);
            people.add(person);
            if (i < 7) entities.persist(new CourierCertification(person, "Rider", "Student" + i, "Campus"));

            CampusOrder order = new CampusOrder("PAGE-" + i, customer, text, "1234", "Station", "Building",
                    i == 8 ? ServiceType.LOBBY : ServiceType.PACKAGE_SMALL);
            order.assignCourier(people.get(i == 6 || i == 7 ? 6 : 0));
            order.updateStatus(i >= 7 ? OrderStatus.CANCELLED : OrderStatus.COMPLETED);
            persistAt("orders", order, time);

            OrderIssue issue = new OrderIssue(order, customer,
                    i == 7 ? OrderIssueType.MISSING_ITEM : OrderIssueType.OTHER, text);
            if (i == 8) issue.claim(administrator);
            persistAt("issues", issue, time);
            UserFeedback item = new UserFeedback(customer, i == 7 ? FeedbackType.ACCOUNT : FeedbackType.OTHER, text);
            if (i == 8) item.claim(administrator);
            persistAt("feedback", item, time);
            persistAt("audit", new AdminAuditLog(administrator,
                    i >= 7 ? AdminAuditAction.USER_RESTORED : AdminAuditAction.USER_SUSPENDED,
                    AdminAuditTargetType.USER, person.getId(), text), time);
        }
        courierId = people.get(0).getId();
        ids.put("couriers", ids.get("users"));
        entities.flush();
        entities.clear();
    }

    private void persistAt(String kind, Object entity, Instant time) {
        ReflectionTestUtils.setField(entity, "createdAt", time);
        entities.persist(entity);
        ids.computeIfAbsent(kind, key -> new ArrayList<>()).add((Long) ReflectionTestUtils.getField(entity, "id"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"orders", "users", "couriers", "issues", "feedback", "audit"})
    void listsWithDataRespectFiltersAndStablePagination(String kind) {
        List<Long> expected = expectedIds(kind);
        assertPages(page -> page(kind, "  mAtCh  ", page), expected);
        List<Long> withoutKeyword = new ArrayList<>(expected);
        withoutKeyword.add(ids.get(kind).get(6));
        assertPages(page -> page(kind, null, page), withoutKeyword);
        assertPages(page -> page(kind, "   ", page), withoutKeyword);
        assertPages(page -> page(kind, "no-matching-record", page), List.of());
    }

    @Test
    void courierOrderPagesExcludeOtherCouriersAndNonPackageOrders() {
        assertPages(page -> {
            var result = couriers.performance(courierId, page, 2).orders();
            return new Page(result.items().stream().map(item -> item.id()).toList(), result.page(),
                    result.size(), result.totalElements(), result.totalPages());
        }, expectedIds("orders"));
    }

    @Test
    void csvExportUsesTheSameFilteredStableOrder() {
        var export = orders.exportCsv("  mAtCh  ", OrderStatus.COMPLETED, false);
        String csv = new String(export.content(), StandardCharsets.UTF_8);
        assertFalse(export.truncated());
        assertEquals(List.of("PAGE-2", "PAGE-1", "PAGE-0", "PAGE-5", "PAGE-4", "PAGE-3"),
                csv.lines().skip(1).map(line -> line.split(",", 2)[0].replace("\"", "")).toList());
    }

    @Test
    void issueOverdueFilterReturnsOnlyOverdueItemsAndAccurateTotal() {
        var result = issues.list("Match", OrderIssueType.OTHER,
                OrderIssueHandlingStatus.PENDING, true, 0, 2);

        assertEquals(6, result.totalElements());
        assertEquals(6, result.overdueTotal());
        assertEquals(2, result.items().size());
        assertTrue(result.items().stream().allMatch(AdminIssueController.AdminIssueItem::overdue));
    }

    private List<Long> expectedIds(String kind) {
        List<Long> values = ids.get(kind);
        return List.of(values.get(2), values.get(1), values.get(0), values.get(5), values.get(4), values.get(3));
    }

    private void assertPages(IntFunction<Page> fetch, List<Long> expected) {
        int totalPages = (expected.size() + 1) / 2;
        List<Long> actual = new ArrayList<>();
        for (int number = 0; number <= totalPages; number++) {
            Page result = fetch.apply(number);
            int start = Math.min(number * 2, expected.size());
            assertEquals(expected.subList(start, Math.min(start + 2, expected.size())), result.ids());
            assertEquals(number, result.number());
            assertEquals(2, result.size());
            assertEquals(expected.size(), result.totalElements());
            assertEquals(totalPages, result.totalPages());
            actual.addAll(result.ids());
        }
        assertEquals(expected, actual);
        assertEquals(expected.subList(0, Math.min(2, expected.size())), fetch.apply(0).ids());
    }

    private Page page(String kind, String query, int number) {
        return switch (kind) {
            case "orders" -> {
                var result = orders.list(query, OrderStatus.COMPLETED, false, number, 2);
                yield new Page(result.items().stream().map(item -> item.id()).toList(), result.page(),
                        result.size(), result.totalElements(), result.totalPages());
            }
            case "users" -> {
                var result = users.list(query, UserRole.COURIER, number, 2);
                yield new Page(result.items().stream().map(item -> item.id()).toList(), result.page(),
                        result.size(), result.totalElements(), result.totalPages());
            }
            case "couriers" -> {
                var result = couriers.list(query, CourierCertificationStatus.PENDING, number, 2);
                yield new Page(result.items().stream().map(item -> item.courierId()).toList(), result.page(),
                        result.size(), result.totalElements(), result.totalPages());
            }
            case "issues" -> {
                var result = issues.list(query, OrderIssueType.OTHER, OrderIssueHandlingStatus.PENDING, number, 2);
                yield new Page(result.items().stream().map(item -> item.id()).toList(), result.page(),
                        result.size(), result.totalElements(), result.totalPages());
            }
            case "feedback" -> {
                var result = feedback.list(query, FeedbackType.OTHER, FeedbackHandlingStatus.PENDING, number, 2);
                yield new Page(result.items().stream().map(item -> item.id()).toList(), result.page(),
                        result.size(), result.totalElements(), result.totalPages());
            }
            case "audit" -> {
                var result = audit.list(query, AdminAuditAction.USER_SUSPENDED, number, 2);
                yield new Page(result.items().stream().map(item -> item.id()).toList(), result.page(),
                        result.size(), result.totalElements(), result.totalPages());
            }
            default -> throw new IllegalArgumentException(kind);
        };
    }

    private record Page(List<Long> ids, int number, int size, long totalElements, int totalPages) { }
}
