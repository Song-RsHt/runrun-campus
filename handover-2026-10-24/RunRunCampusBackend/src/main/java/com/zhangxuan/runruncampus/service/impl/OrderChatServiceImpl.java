package com.zhangxuan.runruncampus.service.impl;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.CampusOrder;
import com.zhangxuan.runruncampus.entity.OrderChatMessage;
import com.zhangxuan.runruncampus.enums.OrderMessageType;
import com.zhangxuan.runruncampus.enums.OrderStatus;
import com.zhangxuan.runruncampus.repository.OrderChatRepository;
import com.zhangxuan.runruncampus.repository.OrderRepository;
import com.zhangxuan.runruncampus.service.OrderChatService;
import com.zhangxuan.runruncampus.service.OrderMessageService;
import com.zhangxuan.runruncampus.vo.OrderChatVO;
import com.zhangxuan.runruncampus.vo.OrderUnreadVO;
import com.zhangxuan.runruncampus.vo.UnreadChatSummaryVO;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;





















@Service
public class OrderChatServiceImpl implements OrderChatService {

    private final OrderRepository orders;

    private final OrderChatRepository chats;

    private final OrderMessageService notifications;

    public OrderChatServiceImpl(OrderRepository orders, OrderChatRepository chats, OrderMessageService notifications) {
        this.orders = orders;
        this.chats = chats;
        this.notifications = notifications;
    }

    @Transactional
    @Override
    public List<OrderChatVO> list(AppUser user, Long orderId) {
        CampusOrder order = orders.findById(orderId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        requireParticipant(user, order);
        chats.markRead(orderId, user.getId(), Instant.now());
        boolean customer = user.getId().equals(order.getCustomer().getId());
        return chats.findByOrderOrderByCreatedAtAsc(order).stream().filter(chat -> customer || chat.getSender().getId().equals(user.getId()) || chat.getRecipient().getId().equals(user.getId())).map(this::response).toList();
    }

    @Transactional(readOnly = true)
    @Override
    public UnreadChatSummaryVO unread(AppUser user) {
        List<OrderUnreadVO> orderCounts = chats.findUnreadCounts(user.getId()).stream().map(count -> new OrderUnreadVO(count.getOrderId(), count.getUnreadCount())).toList();
        long total = orderCounts.stream().mapToLong(OrderUnreadVO::unreadCount).sum();
        return new UnreadChatSummaryVO(total, orderCounts);
    }

    @Transactional
    @Override
    public OrderChatVO send(AppUser sender, Long orderId, String content) {
        String text = content == null ? "" : content.trim();
        if (text.isBlank() || text.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入不超过500字的消息");
        }
        CampusOrder order = orders.findLockedById(orderId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        requireParticipant(sender, order);
        if (order.getStatus() != OrderStatus.ACCEPTED && order.getStatus() != OrderStatus.PICKED_UP) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前订单不能发送消息");
        }
        AppUser recipient = sender.getId().equals(order.getCustomer().getId()) ? order.getCourier() : order.getCustomer();
        OrderChatMessage chat = chats.save(new OrderChatMessage(order, sender, recipient, text));
        notifications.send(recipient, order, OrderMessageType.ORDER_CHAT, "订单有新消息", "请进入订单查看消息");
        return response(chat);
    }

    private void requireParticipant(AppUser user, CampusOrder order) {
        if (!user.getId().equals(order.getCustomer().getId()) && (order.getCourier() == null || !user.getId().equals(order.getCourier().getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅订单当前参与者可查看或发送消息");
        }
    }

    private OrderChatVO response(OrderChatMessage chat) {
        return new OrderChatVO(chat.getId(), chat.getSender().getId(), chat.getSender().getDisplayName(), chat.getRecipient().getId(), chat.getContent(), chat.getCreatedAt());
    }
}
