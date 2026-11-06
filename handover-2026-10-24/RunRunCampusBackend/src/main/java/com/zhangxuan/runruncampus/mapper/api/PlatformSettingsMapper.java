package com.zhangxuan.runruncampus.mapper.api;

import com.zhangxuan.runruncampus.vo.settings.PlatformSettingsVO;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 平台营业与履约设置数据访问；使用调用方事务绑定的 JDBC 连接，不开启或提交事务。 */
@Repository
public class PlatformSettingsMapper {

    private final JdbcTemplate jdbc;

    public PlatformSettingsMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PlatformSettingsVO findSettings() {
        return jdbc.queryForObject(
                """
                select business_hours, wait_minutes, delivery_minutes, max_active_orders,
                    issue_pending_minutes, issue_processing_minutes
                from platform_settings where id = 1
                """,
                (rs, row) ->
                        new PlatformSettingsVO(
                                rs.getString(1),
                                rs.getInt(2),
                                rs.getInt(3),
                                rs.getInt(4),
                                rs.getInt(5),
                                rs.getInt(6)));
    }

    public int updateSettings(
            String hours,
            int waitMinutes,
            int deliveryMinutes,
            int maxActiveOrders,
            int issuePendingMinutes,
            int issueProcessingMinutes) {
        return jdbc.update(
                """
                update platform_settings set business_hours = ?, wait_minutes = ?, delivery_minutes = ?,
                    max_active_orders = ?, issue_pending_minutes = ?, issue_processing_minutes = ?,
                    updated_at = now() where id = 1
                """,
                hours,
                waitMinutes,
                deliveryMinutes,
                maxActiveOrders,
                issuePendingMinutes,
                issueProcessingMinutes);
    }
}
