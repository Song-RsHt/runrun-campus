package com.zhangxuan.runruncampus.repository.message;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.model.message.OrderMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;







public interface OrderMessageRepository extends JpaRepository<OrderMessage, Long> {

  List<OrderMessage> findByRecipientOrderByCreatedAtDesc(AppUser recipient);

  List<OrderMessage> findByRecipientAndReadAtIsNull(AppUser recipient);

  long deleteByRecipient(AppUser recipient);
}
