package com.zhangxuan.runruncampus.repository;

import com.zhangxuan.runruncampus.entity.CampusOrder;
import com.zhangxuan.runruncampus.entity.OrderChatMessage;
import com.zhangxuan.runruncampus.model.UnreadCount;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;












public interface OrderChatRepository extends JpaRepository<OrderChatMessage, Long> {

    List<OrderChatMessage> findByOrderOrderByCreatedAtAsc(CampusOrder order);

    @Modifying
    @Query("update OrderChatMessage chat set chat.readAt = :readAt where chat.order.id = :orderId and" + " chat.recipient.id = :recipientId and chat.readAt is null")
    int markRead(@Param("orderId") Long orderId, @Param("recipientId") Long recipientId, @Param("readAt") Instant readAt);

    @Query("select chat.order.id as orderId, count(chat) as unreadCount " + "from OrderChatMessage chat " + "where chat.recipient.id = :recipientId and chat.readAt is null " + "and (chat.order.customer.id = :recipientId or chat.order.courier.id = :recipientId) " + "group by chat.order.id order by chat.order.id")
    List<UnreadCount> findUnreadCounts(@Param("recipientId") Long recipientId);
}
