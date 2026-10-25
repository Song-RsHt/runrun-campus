package com.zhangxuan.runruncampus.api;

import com.zhangxuan.runruncampus.order.DeliveryZone;
import com.zhangxuan.runruncampus.order.PickupStation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/service-config")
public class ServiceConfigurationController {
    private final PlatformSettingsService settingsService;

    public ServiceConfigurationController(PlatformSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    ConfigurationResponse configuration() {
        List<StationResponse> stations = Arrays.stream(PickupStation.values())
                .map(station -> new StationResponse(station.name(), station.getDisplayName(),
                        station == PickupStation.CAMPUS_PACKAGE_STATION ? "PACKAGE" : "TAKEOUT"))
                .toList();
        List<ZoneResponse> zones = Arrays.stream(DeliveryZone.values())
                .map(zone -> new ZoneResponse(zone.name(), zone.getDisplayName(), zone.getPackageSurchargeCents()))
                .toList();
        return new ConfigurationResponse("无锡学院", settingsService.get().businessHours(), stations, zones);
    }

    public record ConfigurationResponse(String campusName, String businessHours,
                                        List<StationResponse> pickupStations, List<ZoneResponse> deliveryZones) { }
    public record StationResponse(String code, String displayName, String kind) { }
    public record ZoneResponse(String code, String displayName, int packageSurchargeCents) { }
}
