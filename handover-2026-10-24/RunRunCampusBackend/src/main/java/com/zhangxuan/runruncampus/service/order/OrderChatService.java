package com.zhangxuan.runruncampus.service.order;

import com.zhangxuan.runruncampus.entity.user.AppUser;
import com.zhangxuan.runruncampus.vo.order.OrderChatVO;
import com.zhangxuan.runruncampus.vo.order.UnreadChatSummaryVO;

import java.util.List;

/** 订单参与者聊天业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface OrderChatService {

    /**
     * 查询订单参与者聊天列表，保留现有筛选和分页口径。
     *
     * @param user 当前已识别账号
     * @param orderId 订单标识
     * @return 订单参与者聊天列表，顺序和筛选沿用原查询
     */
    List<OrderChatVO> list(AppUser user, Long orderId);

    /**
     * 汇总当前用户未读聊天数量。
     *
     * @param user 当前已识别账号
     * @return 未读聊天汇总
     */
    UnreadChatSummaryVO unread(AppUser user);

    /**
     * 向订单参与者发送订单参与者聊天并保持现有消息权限。
     *
     * @param sender 发送消息的账号
     * @param orderId 订单标识
     * @param content 消息或反馈正文
     * @return 订单参与者聊天
     */
    OrderChatVO send(AppUser sender, Long orderId, String content);
}
