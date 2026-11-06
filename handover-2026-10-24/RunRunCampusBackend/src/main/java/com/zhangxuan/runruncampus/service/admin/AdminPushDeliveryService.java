package com.zhangxuan.runruncampus.service.admin;

import com.zhangxuan.runruncampus.dto.response.admin.PushDeliveryOverview;
import com.zhangxuan.runruncampus.enums.message.PushDeliveryStatus;
import com.zhangxuan.runruncampus.repository.message.PushDeliveryRepository;
import org.springframework.stereotype.Service;

/** Preserves independent status counts and the repository's existing paging semantics. */
@Service
public class AdminPushDeliveryService {

  private final PushDeliveryRepository deliveries;

  public AdminPushDeliveryService(PushDeliveryRepository deliveries) {
    this.deliveries = deliveries;
  }

  public PushDeliveryOverview overview(PushDeliveryStatus status, int page, int size) {
    return new PushDeliveryOverview(
        deliveries.count(PushDeliveryStatus.DEAD),
        deliveries.count(PushDeliveryStatus.PENDING),
        deliveries.count(PushDeliveryStatus.PROCESSING),
        deliveries.page(status, page, size));
  }
}
