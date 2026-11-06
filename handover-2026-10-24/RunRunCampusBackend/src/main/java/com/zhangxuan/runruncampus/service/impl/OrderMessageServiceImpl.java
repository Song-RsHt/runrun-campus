package com.zhangxuan.runruncampus.service.impl;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.CampusOrder;
import com.zhangxuan.runruncampus.entity.OrderMessage;
import com.zhangxuan.runruncampus.enums.OrderMessageType;
import com.zhangxuan.runruncampus.model.OrderPushRequested;
import com.zhangxuan.runruncampus.repository.OrderMessageRepository;
import com.zhangxuan.runruncampus.vo.OrderMessageVO;
import java.time.Instant;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.zhangxuan.runruncampus.service.OrderMessageService;

@Service
public class OrderMessageServiceImpl implements OrderMessageService {

    private final OrderMessageRepository messageRepository;

    private final ApplicationEventPublisher eventPublisher;

    public OrderMessageServiceImpl(OrderMessageRepository messageRepository, ApplicationEventPublisher eventPublisher) {
        this.messageRepository = messageRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    @Override
    public void send(AppUser recipient, CampusOrder order, OrderMessageType type, String title, String content) {
        messageRepository.save(new OrderMessage(recipient, order, type, title, content));
        eventPublisher.publishEvent(OrderPushRequested.create(recipient.getId(), order.getId(), title, content));
    }

    @Transactional(readOnly = true)
    @Override
    public List<OrderMessageVO> list(AppUser recipient) {
        return messageRepository.findByRecipientOrderByCreatedAtDesc(recipient).stream().map(this::response).toList();
    }

    @Transactional
    @Override
    public OrderMessageVO markRead(AppUser recipient, Long id) {
        OrderMessage message = messageRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "消息不存在"));
        if (!recipient.getId().equals(message.getRecipient().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权操作此消息");
        }
        message.markRead();
        return response(message);
    }

    @Transactional
    @Override
    public void markAllRead(AppUser recipient) {
        messageRepository.findByRecipientAndReadAtIsNull(recipient).forEach(OrderMessage::markRead);
    }

    private OrderMessageVO response(OrderMessage message) {
        return new OrderMessageVO(message.getId(), message.getOrder().getId(), message.getOrder().getOrderNo(), message.getType(), message.getTitle(), message.getContent(), message.isRead(), message.getCreatedAt(), message.getReadAt());
    }
}
