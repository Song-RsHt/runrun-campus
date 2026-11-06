package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.enums.OrderIssueType;
import com.zhangxuan.runruncampus.vo.OrderIssueVO;
import java.util.List;





















/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface OrderIssueService {

    OrderIssueVO create(AppUser reporter, Long orderId, OrderIssueType type, String description);

    List<OrderIssueVO> list(AppUser user, Long orderId);
}
