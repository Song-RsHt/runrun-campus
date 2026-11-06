package com.zhangxuan.runruncampus.repository.message;

import com.zhangxuan.runruncampus.entity.message.OrderMessage;
import com.zhangxuan.runruncampus.entity.user.AppUser;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 订单站内消息 JPA 仓储；只定义持久化查询和写入，不承担权限及业务决策。 */
public interface OrderMessageRepository extends JpaRepository<OrderMessage, Long> {

    List<OrderMessage> findByRecipientOrderByCreatedAtDesc(AppUser recipient);

    List<OrderMessage> findByRecipientAndReadAtIsNull(AppUser recipient);

    long deleteByRecipient(AppUser recipient);
}
