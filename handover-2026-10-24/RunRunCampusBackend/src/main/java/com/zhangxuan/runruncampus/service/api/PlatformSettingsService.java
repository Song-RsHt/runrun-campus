package com.zhangxuan.runruncampus.service.api;

import com.zhangxuan.runruncampus.enums.admin.AdminAuditAction;
import com.zhangxuan.runruncampus.enums.admin.AdminAuditTargetType;
import com.zhangxuan.runruncampus.mapper.api.PlatformSettingsMapper;
import com.zhangxuan.runruncampus.model.settings.Settings;
import com.zhangxuan.runruncampus.model.user.AppUser;
import com.zhangxuan.runruncampus.service.admin.AdminAuditService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlatformSettingsService {

  private final PlatformSettingsMapper mapper;

  private final AdminAuditService audit;

  public PlatformSettingsService(PlatformSettingsMapper mapper, AdminAuditService audit) {
    this.mapper = mapper;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public Settings get() {
    return mapper.getValueStatement();
  }

  @Transactional
  public Settings update(AppUser administrator, Settings requested) {
    String hours = requested.businessHours() == null ? "" : requested.businessHours().trim();
    if (hours.isBlank()
        || hours.length() > 80
        || requested.waitMinutes() < 1
        || requested.waitMinutes() > 1440
        || requested.deliveryMinutes() < 1
        || requested.deliveryMinutes() > 1440
        || requested.maxActiveOrders() < 1
        || requested.maxActiveOrders() > 10
        || requested.issuePendingMinutes() < 1
        || requested.issuePendingMinutes() > 1440
        || requested.issueProcessingMinutes() < 1
        || requested.issueProcessingMinutes() > 1440) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "营业时间不能为空，阈值需为1–1440分钟，接单上限需为1–10单");
    }
    mapper.updateWriteStatement(
        hours,
        requested.waitMinutes(),
        requested.deliveryMinutes(),
        requested.maxActiveOrders(),
        requested.issuePendingMinutes(),
        requested.issueProcessingMinutes());
    audit.record(
        administrator,
        AdminAuditAction.PLATFORM_SETTINGS_UPDATED,
        AdminAuditTargetType.PLATFORM_SETTINGS,
        1L,
        "调整运营设置：营业时间 %s，待接 %d 分钟，配送 %d 分钟，骑手上限 %d 单，工单待领取 %d 分钟，处理中 %d 分钟"
            .formatted(
                hours,
                requested.waitMinutes(),
                requested.deliveryMinutes(),
                requested.maxActiveOrders(),
                requested.issuePendingMinutes(),
                requested.issueProcessingMinutes()));
    return get();
  }
}
