package com.zhangxuan.runruncampus.service.impl.message;

import com.zhangxuan.runruncampus.entity.message.OrderMessage;
import com.zhangxuan.runruncampus.entity.order.CampusOrder;
import com.zhangxuan.runruncampus.entity.user.AppUser;
import com.zhangxuan.runruncampus.enums.message.OrderMessageType;
import com.zhangxuan.runruncampus.exception.BusinessFailure;
import com.zhangxuan.runruncampus.model.message.OrderPushRequested;
import com.zhangxuan.runruncampus.repository.message.OrderMessageRepository;
import com.zhangxuan.runruncampus.service.message.OrderMessageService;
import com.zhangxuan.runruncampus.vo.message.OrderMessageVO;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 订单站内消息业务实现；通过注入的业务契约及数据访问组件执行，事务边界保留在本类。 */
@Service
public class OrderMessageServiceImpl implements OrderMessageService {

    private final OrderMessageRepository messageRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderMessageServiceImpl(
            OrderMessageRepository messageRepository, ApplicationEventPublisher eventPublisher) {
        this.messageRepository = messageRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    @Override
    public void send(
            AppUser recipient,
            CampusOrder order,
            OrderMessageType type,
            String title,
            String content) {
        messageRepository.save(new OrderMessage(recipient, order, type, title, content));
        eventPublisher.publishEvent(
                OrderPushRequested.create(recipient.getId(), order.getId(), title, content));
    }

    @Transactional(readOnly = true)
    @Override
    public List<OrderMessageVO> list(AppUser recipient) {
        return messageRepository.findByRecipientOrderByCreatedAtDesc(recipient).stream()
                .map(this::response)
                .toList();
    }

    @Transactional
    @Override
    public OrderMessageVO markRead(AppUser recipient, Long id) {
        OrderMessage message =
                messageRepository.findById(id).orElseThrow(() -> new BusinessFailure(404, "消息不存在"));
        if (!recipient.getId().equals(message.getRecipient().getId())) {
            throw new BusinessFailure(403, "无权操作此消息");
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
        return new OrderMessageVO(
                message.getId(),
                message.getOrder().getId(),
                message.getOrder().getOrderNo(),
                message.getType(),
                message.getTitle(),
                message.getContent(),
                message.isRead(),
                message.getCreatedAt(),
                message.getReadAt());
    }
}
