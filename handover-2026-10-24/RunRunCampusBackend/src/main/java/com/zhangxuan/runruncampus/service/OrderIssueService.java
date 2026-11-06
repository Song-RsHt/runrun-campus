package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.enums.OrderIssueType;
import com.zhangxuan.runruncampus.vo.OrderIssueVO;

import java.util.List;

/** 订单异常上报业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface OrderIssueService {

    /**
     * 创建订单异常上报记录，按现有校验和持久化规则处理。
     *
     * @param reporter 当前异常上报账号
     * @param orderId 订单标识
     * @param type 本次操作的业务分类
     * @param description 订单异常说明
     * @return 订单异常上报
     */
    OrderIssueVO create(AppUser reporter, Long orderId, OrderIssueType type, String description);

    /**
     * 查询订单异常上报列表，保留现有筛选和分页口径。
     *
     * @param user 当前已识别账号
     * @param orderId 订单标识
     * @return 订单异常上报列表，顺序和筛选沿用原查询
     */
    List<OrderIssueVO> list(AppUser user, Long orderId);
}
