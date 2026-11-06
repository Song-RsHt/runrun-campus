package com.zhangxuan.runruncampus.service.impl;

import com.zhangxuan.runruncampus.enums.PushDeliveryStatus;
import com.zhangxuan.runruncampus.repository.PushDeliveryRepository;
import com.zhangxuan.runruncampus.service.AdminPushDeliveryService;
import com.zhangxuan.runruncampus.vo.PushDeliveryOverview;

import org.springframework.stereotype.Service;

/**
 * 管理员推送投递监控业务实现；通过注入的业务契约及数据访问组件执行，事务边界保留在本类。
 *
 * <p>各状态独立计数，分页沿用原有仓储查询语义。
 */
@Service
public class AdminPushDeliveryServiceImpl implements AdminPushDeliveryService {

    private final PushDeliveryRepository deliveries;

    public AdminPushDeliveryServiceImpl(PushDeliveryRepository deliveries) {
        this.deliveries = deliveries;
    }

    @Override
    public PushDeliveryOverview overview(PushDeliveryStatus status, int page, int size) {
        return new PushDeliveryOverview(
                deliveries.count(PushDeliveryStatus.DEAD),
                deliveries.count(PushDeliveryStatus.PENDING),
                deliveries.count(PushDeliveryStatus.PROCESSING),
                deliveries.page(status, page, size));
    }
}
