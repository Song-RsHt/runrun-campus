package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.message.OrderMessageService;
import com.zhangxuan.runruncampus.api.PlatformSettingsService;
import com.zhangxuan.runruncampus.message.OrderMessageType;
import com.zhangxuan.runruncampus.order.CampusOrder;
import com.zhangxuan.runruncampus.order.OrderIssue;
import com.zhangxuan.runruncampus.order.OrderIssueRepository;
import com.zhangxuan.runruncampus.order.OrderIssueType;
import com.zhangxuan.runruncampus.order.ServiceType;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminIssueResultNotificationTest {
    @Test
    void resolvedIssueNotifiesReporter() {
        OrderIssueRepository repository = mock(OrderIssueRepository.class);
        AdminAuditService audit = mock(AdminAuditService.class);
        OrderMessageService messages = mock(OrderMessageService.class);
        AppUser customer = user(1L, UserRole.CUSTOMER);
        AppUser administrator = user(2L, UserRole.ADMIN);
        CampusOrder order = new CampusOrder("RP-ISSUE-RESULT", customer, "商家", "1234", "东门", "宿舍", ServiceType.LOBBY);
        ReflectionTestUtils.setField(order, "id", 11L);
        OrderIssue issue = new OrderIssue(order, customer, OrderIssueType.OTHER, "无法收餐");
        ReflectionTestUtils.setField(issue, "id", 12L);
        issue.claim(administrator);
        when(repository.findLockedById(12L)).thenReturn(Optional.of(issue));

        PlatformSettingsService settings = mock(PlatformSettingsService.class);
        when(settings.get()).thenReturn(new PlatformSettingsService.Settings(
                "09:00–23:00", 10, 30, 10, 30, 120));
        var result = new AdminIssueService(repository, audit, messages, settings)
                .resolve(administrator, 12L, "已联系骑手重新送达");

        assertEquals("已联系骑手重新送达", result.resolution());
        verify(messages).send(eq(customer), eq(order), eq(OrderMessageType.ORDER_ISSUE),
                eq("异常工单已处理"), argThat(content -> content.contains("已联系骑手重新送达")));
    }

    private AppUser user(Long id, UserRole role) {
        AppUser user = new AppUser("1380000000" + id, "hash", "账号" + id, role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
