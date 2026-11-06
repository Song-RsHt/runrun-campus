package com.zhangxuan.runruncampus.controller;

import com.zhangxuan.runruncampus.enums.DeliveryZone;
import com.zhangxuan.runruncampus.enums.PickupStation;
import com.zhangxuan.runruncampus.service.PlatformSettingsService;
import com.zhangxuan.runruncampus.vo.ConfigurationResponse;
import com.zhangxuan.runruncampus.vo.StationResponse;
import com.zhangxuan.runruncampus.vo.ZoneResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/** 服务配置 HTTP 入口；绑定请求并委托业务服务，不直接访问数据库。 */
@RestController
@RequestMapping("/api/v1/service-config")
public class ServiceConfigurationController {

    private final PlatformSettingsService settingsService;

    public ServiceConfigurationController(PlatformSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public ConfigurationResponse configuration() {
        List<StationResponse> stations =
                Arrays.stream(PickupStation.values())
                        .map(
                                station ->
                                        new StationResponse(
                                                station.name(),
                                                station.getDisplayName(),
                                                station == PickupStation.CAMPUS_PACKAGE_STATION
                                                        ? "PACKAGE"
                                                        : "TAKEOUT"))
                        .toList();
        List<ZoneResponse> zones =
                Arrays.stream(DeliveryZone.values())
                        .map(
                                zone ->
                                        new ZoneResponse(
                                                zone.name(),
                                                zone.getDisplayName(),
                                                zone.getPackageSurchargeCents()))
                        .toList();
        return new ConfigurationResponse(
                "无锡学院", settingsService.get().businessHours(), stations, zones);
    }
}
