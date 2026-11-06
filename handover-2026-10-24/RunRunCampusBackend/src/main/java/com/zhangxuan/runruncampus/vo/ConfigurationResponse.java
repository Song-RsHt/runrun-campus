package com.zhangxuan.runruncampus.vo;

import java.util.List;




public record ConfigurationResponse(String campusName, String businessHours, List<StationResponse> pickupStations, List<ZoneResponse> deliveryZones) {
}
