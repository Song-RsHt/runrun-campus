package com.zhangxuan.runruncampus.order;

import com.zhangxuan.runruncampus.message.OrderMessageService;
import com.zhangxuan.runruncampus.message.OrderMessageType;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderIssueServiceTest {
    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderIssueRepository issueRepository;

    @Mock
    private OrderMessageService messageService;

    private OrderIssueService issueService;
    private AppUser customer;
    private CampusOrder order;

    @BeforeEach
    void setUp() {
        issueService = new OrderIssueService(orderRepository, issueRepository, messageService);
        customer = user(1L, UserRole.CUSTOMER);
        order = new CampusOrder("RP-TEST-ISSUE", customer, "测试商家", "1234", "东门", "3号楼", ServiceType.LOBBY);
        ReflectionTestUtils.setField(order, "id", 11L);
    }

    @Test
    void customerCanReportIssueForOwnOrder() {
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));
        when(issueRepository.save(any(OrderIssue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderIssueService.IssueResponse response = issueService.create(
                customer, 11L, OrderIssueType.MISSING_ITEM, "少了一份米饭");

        assertEquals(OrderIssueType.MISSING_ITEM, response.type());
        assertEquals("少了一份米饭", response.description());
        assertEquals(UserRole.CUSTOMER, response.reporterRole());
    }

    @Test
    void assignedCourierCanReportIssue() {
        AppUser courier = user(2L, UserRole.COURIER);
        order.assignCourier(courier);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));
        when(issueRepository.save(any(OrderIssue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderIssueService.IssueResponse response = issueService.create(
                courier, 11L, OrderIssueType.CANNOT_CONTACT, "无法联系收餐人");

        assertEquals(UserRole.COURIER, response.reporterRole());
        verify(messageService).send(eq(customer), eq(order), eq(OrderMessageType.ORDER_ISSUE), anyString(), anyString());
    }

    @Test
    void unrelatedUserCannotReadIssues() {
        AppUser outsider = user(3L, UserRole.CUSTOMER);
        when(orderRepository.findById(11L)).thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> issueService.list(outsider, 11L));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(issueRepository);
    }

    @Test
    void blankDescriptionIsRejected() {
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> issueService.create(customer, 11L, OrderIssueType.OTHER, "   "));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(issueRepository);
    }

    @Test
    void duplicateOpenIssueFromSameReporterAndTypeIsRejected() {
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));
        when(issueRepository.existsByOrderAndReporterAndTypeAndHandlingStatusIn(
                eq(order), eq(customer), eq(OrderIssueType.CANNOT_CONTACT), any())).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> issueService.create(customer, 11L, OrderIssueType.CANNOT_CONTACT, "仍然联系不上对方"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(issueRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void participantCanSeeHandlingStatusAndResolution() {
        AppUser administrator = user(4L, UserRole.ADMIN);
        OrderIssue issue = new OrderIssue(order, customer, OrderIssueType.OTHER, "餐品损坏");
        issue.claim(administrator);
        issue.resolve("已与用户联系并处理");
        when(orderRepository.findById(11L)).thenReturn(Optional.of(order));
        when(issueRepository.findByOrderOrderByCreatedAtAsc(order)).thenReturn(java.util.List.of(issue));

        var result = issueService.list(customer, 11L).get(0);

        assertEquals(OrderIssueHandlingStatus.RESOLVED, result.handlingStatus());
        assertEquals("已与用户联系并处理", result.resolution());
    }

    private AppUser user(Long id, UserRole role) {
        AppUser user = new AppUser("1380000000" + id, "password-hash", "测试账号" + id, role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
