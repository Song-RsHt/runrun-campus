package com.zhangxuan.runruncampus.api;

import com.zhangxuan.runruncampus.admin.AdminPermission;
import com.zhangxuan.runruncampus.auth.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/platform-settings")
public class PlatformSettingsController {
    private final AuthService auth;
    private final PlatformSettingsService settings;

    public PlatformSettingsController(AuthService auth, PlatformSettingsService settings) {
        this.auth = auth;
        this.settings = settings;
    }

    @GetMapping
    PlatformSettingsService.Settings get(@RequestHeader("Authorization") String authorization) {
        auth.requirePermission(authorization, AdminPermission.PLATFORM_SETTINGS_MANAGE);
        return settings.get();
    }

    @PutMapping
    PlatformSettingsService.Settings update(@RequestHeader("Authorization") String authorization,
                                            @Valid @RequestBody UpdateRequest request) {
        var admin = auth.requirePermission(authorization, AdminPermission.PLATFORM_SETTINGS_MANAGE);
        return settings.update(admin, new PlatformSettingsService.Settings(request.businessHours(),
                request.waitMinutes(), request.deliveryMinutes(), request.maxActiveOrders(),
                request.issuePendingMinutes(), request.issueProcessingMinutes()));
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 80) String businessHours,
            @Min(1) @Max(1440) int waitMinutes,
            @Min(1) @Max(1440) int deliveryMinutes,
            @Min(1) @Max(10) int maxActiveOrders,
            @Min(1) @Max(1440) int issuePendingMinutes,
            @Min(1) @Max(1440) int issueProcessingMinutes
    ) { }
}
