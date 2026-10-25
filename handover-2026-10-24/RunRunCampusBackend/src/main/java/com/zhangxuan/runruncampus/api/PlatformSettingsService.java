package com.zhangxuan.runruncampus.api;

import com.zhangxuan.runruncampus.admin.AdminAuditAction;
import com.zhangxuan.runruncampus.admin.AdminAuditService;
import com.zhangxuan.runruncampus.admin.AdminAuditTargetType;
import com.zhangxuan.runruncampus.user.AppUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlatformSettingsService {
    private final JdbcTemplate jdbc;
    private final AdminAuditService audit;

    public PlatformSettingsService(JdbcTemplate jdbc, AdminAuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Settings get() {
        return jdbc.queryForObject("""
                select business_hours, wait_minutes, delivery_minutes, max_active_orders,
                    issue_pending_minutes, issue_processing_minutes
                from platform_settings where id = 1
                """, (rs, row) -> new Settings(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4),
                rs.getInt(5), rs.getInt(6)));
    }

    @Transactional
    public Settings update(AppUser administrator, Settings requested) {
        String hours = requested.businessHours() == null ? "" : requested.businessHours().trim();
        if (hours.isBlank() || hours.length() > 80 || requested.waitMinutes() < 1
                || requested.waitMinutes() > 1440 || requested.deliveryMinutes() < 1
                || requested.deliveryMinutes() > 1440 || requested.maxActiveOrders() < 1
                || requested.maxActiveOrders() > 10 || requested.issuePendingMinutes() < 1
                || requested.issuePendingMinutes() > 1440 || requested.issueProcessingMinutes() < 1
                || requested.issueProcessingMinutes() > 1440) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "营业时间不能为空，阈值需为1–1440分钟，接单上限需为1–10单");
        }
        jdbc.update("""
                update platform_settings set business_hours = ?, wait_minutes = ?, delivery_minutes = ?,
                    max_active_orders = ?, issue_pending_minutes = ?, issue_processing_minutes = ?,
                    updated_at = now() where id = 1
                """, hours, requested.waitMinutes(), requested.deliveryMinutes(), requested.maxActiveOrders(),
                requested.issuePendingMinutes(), requested.issueProcessingMinutes());
        audit.record(administrator, AdminAuditAction.PLATFORM_SETTINGS_UPDATED, AdminAuditTargetType.PLATFORM_SETTINGS,
                1L, "调整运营设置：营业时间 %s，待接 %d 分钟，配送 %d 分钟，骑手上限 %d 单，工单待领取 %d 分钟，处理中 %d 分钟".formatted(
                        hours, requested.waitMinutes(), requested.deliveryMinutes(), requested.maxActiveOrders(),
                        requested.issuePendingMinutes(), requested.issueProcessingMinutes()));
        return get();
    }

    public record Settings(String businessHours, int waitMinutes, int deliveryMinutes, int maxActiveOrders,
                           int issuePendingMinutes, int issueProcessingMinutes) { }
}
