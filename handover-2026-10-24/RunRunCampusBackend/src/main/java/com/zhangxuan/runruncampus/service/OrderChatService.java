package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.vo.OrderChatVO;
import com.zhangxuan.runruncampus.vo.UnreadChatSummaryVO;
import java.util.List;




















/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface OrderChatService {

    List<OrderChatVO> list(AppUser user, Long orderId);

    UnreadChatSummaryVO unread(AppUser user);

    OrderChatVO send(AppUser sender, Long orderId, String content);
}
