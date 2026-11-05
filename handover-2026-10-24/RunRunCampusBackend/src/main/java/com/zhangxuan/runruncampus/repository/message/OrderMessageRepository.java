package com.zhangxuan.runruncampus.repository.message;
import com.zhangxuan.runruncampus.model.message.OrderMessage;

import com.zhangxuan.runruncampus.model.user.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderMessageRepository extends JpaRepository<OrderMessage, Long> {
    List<OrderMessage> findByRecipientOrderByCreatedAtDesc(AppUser recipient);
    List<OrderMessage> findByRecipientAndReadAtIsNull(AppUser recipient);
    long deleteByRecipient(AppUser recipient);
}
