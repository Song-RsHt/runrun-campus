package com.zhangxuan.runrunv2.vo;

import com.zhangxuan.runrunv2.model.OrderSchedule;
import java.util.List;















public record ServiceConfigVO(List<ServiceOptionVO> services, List<DeliveryZoneVO> zones, List<ServicePointVO> servicePoints, OrderSchedule businessSchedule, boolean newOrderEnabled, String reviewOwnerId, long configVersion, String id, long version) {
}
