package com.zhangxuan.runrunv2.vo;

import com.zhangxuan.runrunv2.model.Schedule;
import java.util.List;











public record ServiceConfigVO(List<ServiceOptionVO> services, List<DeliveryZoneVO> zones, List<ServicePointVO> servicePoints, Schedule businessSchedule, boolean newOrderEnabled, String reviewOwnerId, long configVersion, String id, long version) {
}
