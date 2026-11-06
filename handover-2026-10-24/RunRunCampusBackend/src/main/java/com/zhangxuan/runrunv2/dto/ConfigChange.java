package com.zhangxuan.runrunv2.dto;

import com.zhangxuan.runrunv2.model.Schedule;
import java.util.List;





public record ConfigChange(Long configVersion, Long expectedVersion, Boolean newOrderEnabled, String reviewOwnerId, Schedule businessSchedule, List<ZoneChange> zones, List<PointChange> servicePoints, String reason) {
}
