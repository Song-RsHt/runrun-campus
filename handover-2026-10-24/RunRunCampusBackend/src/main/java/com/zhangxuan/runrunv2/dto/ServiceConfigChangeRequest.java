package com.zhangxuan.runrunv2.dto;

import com.zhangxuan.runrunv2.model.OrderSchedule;
import java.util.List;

public record ServiceConfigChangeRequest(Long configVersion, Long expectedVersion, Boolean newOrderEnabled, String reviewOwnerId, OrderSchedule businessSchedule, List<DeliveryZoneChangeRequest> zones, List<ServicePointChangeRequest> servicePoints, String reason) {
}
