package com.zhangxuan.runruncampus.dto.response.api.serviceconfiguration;

import java.util.List;

public record ConfigurationResponse(
    String campusName,
    String businessHours,
    List<StationResponse> pickupStations,
    List<ZoneResponse> deliveryZones) {}
