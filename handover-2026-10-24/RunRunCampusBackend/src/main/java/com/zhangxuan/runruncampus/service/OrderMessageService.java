package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.CampusOrder;
import com.zhangxuan.runruncampus.enums.OrderMessageType;
import com.zhangxuan.runruncampus.vo.OrderMessageVO;
import java.util.List;

















/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface OrderMessageService {

    void send(AppUser recipient, CampusOrder order, OrderMessageType type, String title, String content);

    List<OrderMessageVO> list(AppUser recipient);

    OrderMessageVO markRead(AppUser recipient, Long id);

    void markAllRead(AppUser recipient);
}
