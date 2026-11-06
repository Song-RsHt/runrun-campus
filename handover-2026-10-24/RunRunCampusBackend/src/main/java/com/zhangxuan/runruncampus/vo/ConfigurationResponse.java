package com.zhangxuan.runruncampus.vo;

import java.util.List;

/** 配置响应；保持现有 JSON 字段及序列化契约。 */
public record ConfigurationResponse(
        String campusName,
        String businessHours,
        List<StationResponse> pickupStations,
        List<ZoneResponse> deliveryZones) {}
