package com.zhangxuan.runruncampus.repository;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.OrderMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;







public interface OrderMessageRepository extends JpaRepository<OrderMessage, Long> {

    List<OrderMessage> findByRecipientOrderByCreatedAtDesc(AppUser recipient);

    List<OrderMessage> findByRecipientAndReadAtIsNull(AppUser recipient);

    long deleteByRecipient(AppUser recipient);
}
