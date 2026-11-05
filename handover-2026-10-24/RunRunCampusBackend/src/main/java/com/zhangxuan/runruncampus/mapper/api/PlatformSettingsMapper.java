package com.zhangxuan.runruncampus.mapper.api;

import com.zhangxuan.runruncampus.model.settings.Settings;
import org.springframework.stereotype.Repository;

/**
 * SQL for PlatformSettingsService. Uses the caller's transaction-bound JDBC connection; never opens
 * or commits a transaction.
 */
@Repository
public class PlatformSettingsMapper {

  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  public PlatformSettingsMapper(org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public com.zhangxuan.runruncampus.model.settings.Settings getValueStatement() {
    return jdbc.queryForObject(
        """
        select business_hours, wait_minutes, delivery_minutes, max_active_orders,
            issue_pending_minutes, issue_processing_minutes
        from platform_settings where id = 1
        """,
        (rs, row) ->
            new Settings(
                rs.getString(1),
                rs.getInt(2),
                rs.getInt(3),
                rs.getInt(4),
                rs.getInt(5),
                rs.getInt(6)));
  }

  public int updateWriteStatement(
      java.lang.String hours,
      int parameter2,
      int parameter3,
      int parameter4,
      int parameter5,
      int parameter6) {
    return jdbc.update(
        """
        update platform_settings set business_hours = ?, wait_minutes = ?, delivery_minutes = ?,
            max_active_orders = ?, issue_pending_minutes = ?, issue_processing_minutes = ?,
            updated_at = now() where id = 1
        """,
        hours,
        parameter2,
        parameter3,
        parameter4,
        parameter5,
        parameter6);
  }
}
