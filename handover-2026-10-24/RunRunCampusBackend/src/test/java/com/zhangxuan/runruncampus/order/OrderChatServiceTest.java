package com.zhangxuan.runruncampus.order;

import com.zhangxuan.runruncampus.message.OrderMessageService;
import com.zhangxuan.runruncampus.message.OrderMessageType;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderChatServiceTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderChatRepository chats = mock(OrderChatRepository.class);
    private final OrderMessageService notifications = mock(OrderMessageService.class);
    private final OrderChatService service = new OrderChatService(orders, chats, notifications);
    private final AppUser customer = user(1L, UserRole.CUSTOMER);
    private final AppUser courier = user(2L, UserRole.COURIER);
    private final AppUser newCourier = user(3L, UserRole.COURIER);

    @Test
    void currentParticipantsCanExchangeTextAndRecipientGetsGenericNotification() {
        CampusOrder order = order();
        order.assignCourier(courier);
        when(orders.findLockedById(11L)).thenReturn(Optional.of(order));
        when(chats.save(any(OrderChatMessage.class))).thenAnswer(call -> call.getArgument(0));

        var sent = service.send(customer, 11L, "请放到一号楼门口");

        assertEquals("请放到一号楼门口", sent.content());
        verify(notifications).send(eq(courier), eq(order), eq(OrderMessageType.ORDER_CHAT),
                eq("订单有新消息"), eq("请进入订单查看消息"));
    }

    @Test
    void unassignedAndReplacedCouriersCannotSend() {
        CampusOrder order = order();
        order.assignCourier(courier);
        order.requestReassignment();
        order.assignCourier(newCourier);
        when(orders.findLockedById(11L)).thenReturn(Optional.of(order));

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.send(courier, 11L, "我还能联系用户吗"));

        assertEquals(HttpStatus.FORBIDDEN, failure.getStatusCode());
    }

    @Test
    void newCourierDoesNotReadPreviousCouriersConversation() {
        CampusOrder order = order();
        order.assignCourier(courier);
        OrderChatMessage oldMessage = new OrderChatMessage(order, customer, courier, "旧骑手的消息");
        order.requestReassignment();
        order.assignCourier(newCourier);
        OrderChatMessage currentMessage = new OrderChatMessage(order, customer, newCourier, "新骑手的消息");
        when(orders.findById(11L)).thenReturn(Optional.of(order));
        when(chats.findByOrderOrderByCreatedAtAsc(order)).thenReturn(List.of(oldMessage, currentMessage));

        var result = service.list(newCourier, 11L);

        assertEquals(1, result.size());
        assertEquals("新骑手的消息", result.get(0).content());
        assertEquals(2, service.list(customer, 11L).size());
    }

    @Test
    void openingConversationMarksMessagesAddressedToCurrentUserRead() {
        CampusOrder order = order();
        order.assignCourier(courier);
        when(orders.findById(11L)).thenReturn(Optional.of(order));
        when(chats.findByOrderOrderByCreatedAtAsc(order)).thenReturn(List.of());

        service.list(customer, 11L);

        verify(chats).markRead(eq(11L), eq(customer.getId()), any(java.time.Instant.class));
    }

    @Test
    void unreadSummaryContainsTotalAndCountsByOrder() {
        OrderChatRepository.UnreadCount first = mock(OrderChatRepository.UnreadCount.class);
        OrderChatRepository.UnreadCount second = mock(OrderChatRepository.UnreadCount.class);
        when(first.getOrderId()).thenReturn(11L);
        when(first.getUnreadCount()).thenReturn(2L);
        when(second.getOrderId()).thenReturn(12L);
        when(second.getUnreadCount()).thenReturn(1L);
        when(chats.findUnreadCounts(customer.getId())).thenReturn(List.of(first, second));

        var result = service.unread(customer);

        assertEquals(3L, result.total());
        assertEquals(2L, result.orders().get(0).unreadCount());
        verify(chats).findUnreadCounts(anyLong());
    }

    @Test
    void completedOrderIsReadOnly() {
        CampusOrder order = order();
        order.assignCourier(courier);
        order.updateStatus(OrderStatus.COMPLETED);
        when(orders.findLockedById(11L)).thenReturn(Optional.of(order));

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.send(customer, 11L, "订单完成后留言"));

        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
    }

    private CampusOrder order() {
        CampusOrder order = new CampusOrder("RP-CHAT-1", customer, "商家", "1234", "东门", "宿舍", ServiceType.LOBBY);
        ReflectionTestUtils.setField(order, "id", 11L);
        return order;
    }

    private AppUser user(Long id, UserRole role) {
        AppUser user = new AppUser("1380000000" + id, "hash", "账号" + id, role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
