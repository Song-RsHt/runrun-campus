package com.zhangxuan.runruncampus.service.message;

import com.zhangxuan.runruncampus.entity.order.CampusOrder;
import com.zhangxuan.runruncampus.entity.user.AppUser;
import com.zhangxuan.runruncampus.enums.message.OrderMessageType;
import com.zhangxuan.runruncampus.vo.message.OrderMessageVO;

import java.util.List;

/** 订单站内消息业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface OrderMessageService {

    /**
     * 向订单参与者发送订单站内消息并保持现有消息权限。
     *
     * @param recipient 接收消息的账号
     * @param order 调用方已读取并按原规则保护的订单
     * @param type 本次操作的业务分类
     * @param title 站内消息标题
     * @param content 消息或反馈正文
     */
    void send(
            AppUser recipient,
            CampusOrder order,
            OrderMessageType type,
            String title,
            String content);

    /**
     * 查询订单站内消息列表，保留现有筛选和分页口径。
     *
     * @param recipient 接收消息的账号
     * @return 订单站内消息列表，顺序和筛选沿用原查询
     */
    List<OrderMessageVO> list(AppUser recipient);

    /**
     * 标记当前账号有权读取的指定消息为已读。
     *
     * @param recipient 接收消息的账号
     * @param id 目标业务记录标识
     * @return 订单站内消息
     */
    OrderMessageVO markRead(AppUser recipient, Long id);

    /**
     * 标记当前账号的全部站内消息为已读。
     *
     * @param recipient 接收消息的账号
     */
    void markAllRead(AppUser recipient);
}
