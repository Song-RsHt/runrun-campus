package com.zhangxuan.runruncampus.service.impl;

import com.zhangxuan.runruncampus.enums.PushDeliveryStatus;
import com.zhangxuan.runruncampus.repository.PushDeliveryRepository;
import com.zhangxuan.runruncampus.vo.PushDeliveryOverview;
import org.springframework.stereotype.Service;
import com.zhangxuan.runruncampus.service.AdminPushDeliveryService;

/**
 * Preserves independent status counts and the repository's existing paging semantics.
 */
@Service
public class AdminPushDeliveryServiceImpl implements AdminPushDeliveryService {

    private final PushDeliveryRepository deliveries;

    public AdminPushDeliveryServiceImpl(PushDeliveryRepository deliveries) {
        this.deliveries = deliveries;
    }

    @Override
    public PushDeliveryOverview overview(PushDeliveryStatus status, int page, int size) {
        return new PushDeliveryOverview(deliveries.count(PushDeliveryStatus.DEAD), deliveries.count(PushDeliveryStatus.PENDING), deliveries.count(PushDeliveryStatus.PROCESSING), deliveries.page(status, page, size));
    }
}
