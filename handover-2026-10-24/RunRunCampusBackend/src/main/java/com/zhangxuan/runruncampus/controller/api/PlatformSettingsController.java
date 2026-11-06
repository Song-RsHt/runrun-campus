package com.zhangxuan.runruncampus.controller.api;

import com.zhangxuan.runruncampus.dto.request.api.platformsettings.UpdateRequest;
import com.zhangxuan.runruncampus.enums.admin.AdminPermission;
import com.zhangxuan.runruncampus.model.settings.Settings;
import com.zhangxuan.runruncampus.service.api.PlatformSettingsService;
import com.zhangxuan.runruncampus.service.auth.AuthService;
import jakarta.validation.Valid;
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
  public Settings get(@RequestHeader("Authorization") String authorization) {
    auth.requirePermission(authorization, AdminPermission.PLATFORM_SETTINGS_MANAGE);
    return settings.get();
  }

  @PutMapping
  public Settings update(
      @RequestHeader("Authorization") String authorization,
      @Valid @RequestBody UpdateRequest request) {
    var admin = auth.requirePermission(authorization, AdminPermission.PLATFORM_SETTINGS_MANAGE);
    return settings.update(
        admin,
        new Settings(
            request.businessHours(),
            request.waitMinutes(),
            request.deliveryMinutes(),
            request.maxActiveOrders(),
            request.issuePendingMinutes(),
            request.issueProcessingMinutes()));
  }
}
