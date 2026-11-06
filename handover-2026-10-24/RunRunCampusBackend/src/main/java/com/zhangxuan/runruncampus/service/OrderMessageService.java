package com.zhangxuan.runruncampus.service;

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

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.zhangxuan.runruncampus.exception.BusinessFailure;

/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface OrderMessageService {

    void send(AppUser recipient, CampusOrder order, OrderMessageType type, String title, String content);

    List<OrderMessageVO> list(AppUser recipient);

    OrderMessageVO markRead(AppUser recipient, Long id);

    void markAllRead(AppUser recipient);
}
