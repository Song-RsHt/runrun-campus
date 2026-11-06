package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.enums.PushDeliveryStatus;
import com.zhangxuan.runruncampus.vo.PushDeliveryOverview;







/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface AdminPushDeliveryService {

    PushDeliveryOverview overview(PushDeliveryStatus status, int page, int size);
}
