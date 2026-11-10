package com.zhangxuan.runruncampus.controller.api;

import com.zhangxuan.runruncampus.dto.api.PlatformSettingsUpdateRequest;
import com.zhangxuan.runruncampus.enums.admin.AdminPermission;
import com.zhangxuan.runruncampus.service.api.PlatformSettingsService;
import com.zhangxuan.runruncampus.service.auth.AuthService;
import com.zhangxuan.runruncampus.vo.settings.PlatformSettingsVO;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 平台营业与履约设置 HTTP 入口；绑定请求并委托业务服务，不直接访问数据库。 */
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
    public PlatformSettingsVO get(@RequestHeader("Authorization") String authorization) {
        auth.requirePermission(authorization, AdminPermission.PLATFORM_SETTINGS_MANAGE);
        return settings.get();
    }

    @PutMapping
    public PlatformSettingsVO update(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody PlatformSettingsUpdateRequest request) {
        var admin = auth.requirePermission(authorization, AdminPermission.PLATFORM_SETTINGS_MANAGE);
        return settings.update(
                admin,
                new PlatformSettingsVO(
                        request.businessHours(),
                        request.waitMinutes(),
                        request.deliveryMinutes(),
                        request.maxActiveOrders(),
                        request.issuePendingMinutes(),
                        request.issueProcessingMinutes()));
    }
}
