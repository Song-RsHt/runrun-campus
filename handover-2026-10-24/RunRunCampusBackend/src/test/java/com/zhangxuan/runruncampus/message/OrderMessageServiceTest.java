package com.zhangxuan.runruncampus.message;

import com.zhangxuan.runruncampus.order.CampusOrder;
import com.zhangxuan.runruncampus.order.ServiceType;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class OrderMessageServiceTest {
    @Mock
    private OrderMessageRepository messageRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private OrderMessageService messageService;
    private AppUser recipient;
    private CampusOrder order;

    @BeforeEach
    void setUp() {
        messageService = new OrderMessageService(messageRepository, eventPublisher);
        recipient = user(1L);
        order = new CampusOrder("RP-MESSAGE-001", recipient, "测试商家", "1234", "东门", "3号楼", ServiceType.LOBBY);
        ReflectionTestUtils.setField(order, "id", 11L);
    }

    @Test
    void savingMessagePublishesPushRequest() {
        messageService.send(recipient, order, OrderMessageType.ORDER_ACCEPTED,
                "骑手已接单", "测试骑手已接单");

        verify(messageRepository).save(any(OrderMessage.class));
        ArgumentCaptor<OrderPushRequested> captor = ArgumentCaptor.forClass(OrderPushRequested.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(1L, captor.getValue().recipientId());
        assertEquals(11L, captor.getValue().orderId());
        assertEquals("骑手已接单", captor.getValue().title());
        assertEquals(36, captor.getValue().idempotentToken().length());
    }

    @Test
    void listsOnlyRecipientsMessages() {
        OrderMessage message = message(21L, recipient);
        when(messageRepository.findByRecipientOrderByCreatedAtDesc(recipient)).thenReturn(List.of(message));

        List<OrderMessageService.MessageResponse> responses = messageService.list(recipient);

        assertEquals(1, responses.size());
        assertFalse(responses.get(0).read());
        assertEquals(11L, responses.get(0).orderId());
    }

    @Test
    void recipientCanMarkMessageRead() {
        OrderMessage message = message(21L, recipient);
        when(messageRepository.findById(21L)).thenReturn(Optional.of(message));

        OrderMessageService.MessageResponse response = messageService.markRead(recipient, 21L);

        assertTrue(response.read());
        assertNotNull(response.readAt());
    }

    @Test
    void anotherUserCannotMarkMessageRead() {
        OrderMessage message = message(21L, recipient);
        when(messageRepository.findById(21L)).thenReturn(Optional.of(message));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> messageService.markRead(user(2L), 21L));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertFalse(message.isRead());
    }

    @Test
    void recipientCanMarkAllMessagesRead() {
        OrderMessage first = message(21L, recipient);
        OrderMessage second = message(22L, recipient);
        when(messageRepository.findByRecipientAndReadAtIsNull(recipient)).thenReturn(List.of(first, second));

        messageService.markAllRead(recipient);

        assertTrue(first.isRead());
        assertTrue(second.isRead());
    }

    private OrderMessage message(Long id, AppUser user) {
        OrderMessage message = new OrderMessage(user, order, OrderMessageType.ORDER_ACCEPTED,
                "骑手已接单", "测试骑手已接单");
        ReflectionTestUtils.setField(message, "id", id);
        return message;
    }

    private AppUser user(Long id) {
        AppUser user = new AppUser("1380000000" + id, "password-hash", "测试账号" + id, UserRole.CUSTOMER);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
