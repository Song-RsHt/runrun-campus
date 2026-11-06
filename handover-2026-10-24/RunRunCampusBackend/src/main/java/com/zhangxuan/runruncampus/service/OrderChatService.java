package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.CampusOrder;
import com.zhangxuan.runruncampus.entity.OrderChatMessage;
import com.zhangxuan.runruncampus.enums.OrderStatus;
import com.zhangxuan.runruncampus.enums.OrderMessageType;
import com.zhangxuan.runruncampus.repository.OrderChatRepository;
import com.zhangxuan.runruncampus.repository.OrderRepository;
import com.zhangxuan.runruncampus.service.OrderMessageService;
import com.zhangxuan.runruncampus.vo.OrderChatVO;
import com.zhangxuan.runruncampus.vo.OrderUnreadVO;
import com.zhangxuan.runruncampus.vo.UnreadChatSummaryVO;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.zhangxuan.runruncampus.exception.BusinessFailure;

/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface OrderChatService {

    List<OrderChatVO> list(AppUser user, Long orderId);

    UnreadChatSummaryVO unread(AppUser user);

    OrderChatVO send(AppUser sender, Long orderId, String content);
}
