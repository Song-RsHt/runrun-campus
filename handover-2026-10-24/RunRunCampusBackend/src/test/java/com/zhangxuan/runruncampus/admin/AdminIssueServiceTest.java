package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.api.PlatformSettingsService;
import com.zhangxuan.runruncampus.message.OrderMessageService;
import com.zhangxuan.runruncampus.order.CampusOrder;
import com.zhangxuan.runruncampus.order.OrderIssue;
import com.zhangxuan.runruncampus.order.OrderIssueHandlingStatus;
import com.zhangxuan.runruncampus.order.OrderIssueRepository;
import com.zhangxuan.runruncampus.order.OrderIssueType;
import com.zhangxuan.runruncampus.order.ServiceType;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminIssueServiceTest {
    private final OrderIssueRepository repository = mock(OrderIssueRepository.class);
    private final PlatformSettingsService settings = mock(PlatformSettingsService.class);
    private final AdminIssueService service = new AdminIssueService(repository,
            mock(AdminAuditService.class), mock(OrderMessageService.class), settings);

    @Test
    void pendingIssuePastDeadlineIsMarkedOverdueAndCounted() {
        AppUser customer = user(1L, UserRole.CUSTOMER);
        CampusOrder order = new CampusOrder("RP-SLA-1", customer, "商家", "1234", "东门", "宿舍", ServiceType.LOBBY);
        ReflectionTestUtils.setField(order, "id", 11L);
        OrderIssue issue = new OrderIssue(order, customer, OrderIssueType.CANNOT_CONTACT, "联系不上对方");
        ReflectionTestUtils.setField(issue, "id", 12L);
        ReflectionTestUtils.setField(issue, "createdAt", Instant.now().minus(40, ChronoUnit.MINUTES));
        when(settings.get()).thenReturn(new PlatformSettingsService.Settings(
                "09:00–23:00", 10, 30, 10, 30, 120));
        when(repository.filterForAdmin(eq(null), eq(OrderIssueHandlingStatus.PENDING), eq(false),
                any(Instant.class), any(Instant.class), any())).thenReturn(new PageImpl<>(List.of(issue)));
        when(repository.filterForAdmin(eq(null), eq(OrderIssueHandlingStatus.PENDING), eq(true),
                any(Instant.class), any(Instant.class), any())).thenReturn(new PageImpl<>(List.of(issue)));

        var result = service.list(null, null, OrderIssueHandlingStatus.PENDING, false, 0, 20);

        assertTrue(result.items().get(0).overdue());
        assertTrue(result.items().get(0).overdueMinutes() >= 10);
        assertTrue(result.overdueTotal() == 1);
        verify(repository).filterForAdmin(eq(null), eq(OrderIssueHandlingStatus.PENDING), eq(true),
                any(Instant.class), any(Instant.class), any());
    }

    private AppUser user(Long id, UserRole role) {
        AppUser user = new AppUser("1380000000" + id, "hash", "账号" + id, role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
