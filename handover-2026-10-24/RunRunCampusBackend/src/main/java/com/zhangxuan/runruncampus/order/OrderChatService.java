package com.zhangxuan.runruncampus.order;

import com.zhangxuan.runruncampus.message.OrderMessageService;
import com.zhangxuan.runruncampus.message.OrderMessageType;
import com.zhangxuan.runruncampus.user.AppUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class OrderChatService {
    private final OrderRepository orders;
    private final OrderChatRepository chats;
    private final OrderMessageService notifications;

    public OrderChatService(OrderRepository orders, OrderChatRepository chats, OrderMessageService notifications) {
        this.orders = orders;
        this.chats = chats;
        this.notifications = notifications;
    }

    @Transactional
    public List<ChatResponse> list(AppUser user, Long orderId) {
        CampusOrder order = orders.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        requireParticipant(user, order);
        chats.markRead(orderId, user.getId(), Instant.now());
        boolean customer = user.getId().equals(order.getCustomer().getId());
        return chats.findByOrderOrderByCreatedAtAsc(order).stream()
                .filter(chat -> customer || chat.getSender().getId().equals(user.getId())
                        || chat.getRecipient().getId().equals(user.getId()))
                .map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public UnreadSummaryResponse unread(AppUser user) {
        List<OrderUnreadResponse> orderCounts = chats.findUnreadCounts(user.getId()).stream()
                .map(count -> new OrderUnreadResponse(count.getOrderId(), count.getUnreadCount()))
                .toList();
        long total = orderCounts.stream().mapToLong(OrderUnreadResponse::unreadCount).sum();
        return new UnreadSummaryResponse(total, orderCounts);
    }

    @Transactional
    public ChatResponse send(AppUser sender, Long orderId, String content) {
        String text = content == null ? "" : content.trim();
        if (text.isBlank() || text.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入不超过500字的消息");
        }
        CampusOrder order = orders.findLockedById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        requireParticipant(sender, order);
        if (order.getStatus() != OrderStatus.ACCEPTED && order.getStatus() != OrderStatus.PICKED_UP) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前订单不能发送消息");
        }
        AppUser recipient = sender.getId().equals(order.getCustomer().getId())
                ? order.getCourier() : order.getCustomer();
        OrderChatMessage chat = chats.save(new OrderChatMessage(order, sender, recipient, text));
        notifications.send(recipient, order, OrderMessageType.ORDER_CHAT,
                "订单有新消息", "请进入订单查看消息");
        return response(chat);
    }

    private void requireParticipant(AppUser user, CampusOrder order) {
        if (!user.getId().equals(order.getCustomer().getId())
                && (order.getCourier() == null || !user.getId().equals(order.getCourier().getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅订单当前参与者可查看或发送消息");
        }
    }

    private ChatResponse response(OrderChatMessage chat) {
        return new ChatResponse(chat.getId(), chat.getSender().getId(), chat.getSender().getDisplayName(),
                chat.getRecipient().getId(), chat.getContent(), chat.getCreatedAt());
    }

    public record ChatResponse(Long id, Long senderId, String senderName, Long recipientId,
                               String content, Instant createdAt) { }

    public record UnreadSummaryResponse(long total, List<OrderUnreadResponse> orders) { }

    public record OrderUnreadResponse(Long orderId, long unreadCount) { }
}
