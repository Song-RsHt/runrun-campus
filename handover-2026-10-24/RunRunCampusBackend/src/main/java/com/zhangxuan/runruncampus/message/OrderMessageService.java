package com.zhangxuan.runruncampus.message;

import com.zhangxuan.runruncampus.order.CampusOrder;
import com.zhangxuan.runruncampus.user.AppUser;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class OrderMessageService {
    private final OrderMessageRepository messageRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderMessageService(OrderMessageRepository messageRepository, ApplicationEventPublisher eventPublisher) {
        this.messageRepository = messageRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void send(AppUser recipient, CampusOrder order, OrderMessageType type, String title, String content) {
        messageRepository.save(new OrderMessage(recipient, order, type, title, content));
        eventPublisher.publishEvent(OrderPushRequested.create(recipient.getId(), order.getId(), title, content));
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> list(AppUser recipient) {
        return messageRepository.findByRecipientOrderByCreatedAtDesc(recipient).stream().map(this::response).toList();
    }

    @Transactional
    public MessageResponse markRead(AppUser recipient, Long id) {
        OrderMessage message = messageRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "消息不存在"));
        if (!recipient.getId().equals(message.getRecipient().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权操作此消息");
        }
        message.markRead();
        return response(message);
    }

    @Transactional
    public void markAllRead(AppUser recipient) {
        messageRepository.findByRecipientAndReadAtIsNull(recipient).forEach(OrderMessage::markRead);
    }

    private MessageResponse response(OrderMessage message) {
        return new MessageResponse(message.getId(), message.getOrder().getId(), message.getOrder().getOrderNo(),
                message.getType(), message.getTitle(), message.getContent(), message.isRead(),
                message.getCreatedAt(), message.getReadAt());
    }

    public record MessageResponse(Long id, Long orderId, String orderNo, OrderMessageType type,
                                  String title, String content, boolean read, Instant createdAt, Instant readAt) { }
}
