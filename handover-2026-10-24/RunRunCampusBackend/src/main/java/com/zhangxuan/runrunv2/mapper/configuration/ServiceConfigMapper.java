package com.zhangxuan.runrunv2.mapper.configuration;

import static com.zhangxuan.runrunv2.model.order.OrderModels.*;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Repository;

/**
 * SQL for ServiceConfigService. Uses the caller's transaction-bound JDBC connection; never opens or
 * commits a transaction.
 */
@Repository
public class ServiceConfigMapper {

  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  public ServiceConfigMapper(org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public java.util.Map<java.lang.String, java.lang.Object> settingsRowPlatformSettings(
      boolean update) {
    return jdbc.queryForMap(
        "SELECT * FROM runrun_v2.platform_settings WHERE id=1 FOR "
            + (update ? "UPDATE" : "SHARE"));
  }

  public java.util.List<com.zhangxuan.runrunv2.model.order.OrderModels.Zone> viewRowsDeliveryZones(
      java.lang.String filter) {
    return jdbc.query(
        "SELECT * FROM runrun_v2.delivery_zones" + filter + " ORDER BY id",
        (r, n) ->
            new Zone(
                r.getString("id"),
                r.getString("code"),
                r.getString("name"),
                r.getLong("package_surcharge_cents"),
                r.getBoolean("enabled"),
                r.getLong("config_version"),
                r.getLong("version")));
  }

  public java.util.List<com.zhangxuan.runrunv2.model.order.OrderModels.ServicePoint>
      viewRowsServicePoints(java.lang.String filter) {
    return jdbc.query(
        "SELECT * FROM runrun_v2.service_points" + filter + " ORDER BY id",
        (r, n) ->
            new ServicePoint(
                r.getString("id"),
                r.getString("code"),
                r.getString("name"),
                r.getBigDecimal("latitude"),
                r.getBigDecimal("longitude"),
                r.getString("coordinate_system"),
                r.getBoolean("enabled"),
                r.getLong("config_version"),
                r.getLong("version")));
  }

  public java.util.List<java.util.Map<java.lang.String, java.lang.Object>> enabledRowsStatement(
      java.lang.String table, java.lang.String code) {
    return jdbc.queryForList(
        "SELECT * FROM runrun_v2." + table + " WHERE code=? AND enabled", code);
  }

  public int patchWriteDeliveryZones(
      java.lang.String parameter1,
      java.lang.String parameter2,
      java.lang.Long parameter3,
      java.lang.Boolean parameter4,
      long parameter5) {
    return jdbc.update(
        "INSERT INTO"
            + " runrun_v2.delivery_zones(code,name,package_surcharge_cents,enabled,config_version)"
            + " VALUES(?,?,?,?,?) ON CONFLICT(code) DO UPDATE SET"
            + " name=excluded.name,package_surcharge_cents=excluded.package_surcharge_cents,enabled=excluded.enabled,config_version=excluded.config_version,version=delivery_zones.version+1,updated_at=now()",
        parameter1,
        parameter2,
        parameter3,
        parameter4,
        parameter5);
  }

  public int patchWriteServicePoints(
      java.lang.String parameter1,
      java.lang.String parameter2,
      java.math.BigDecimal parameter3,
      java.math.BigDecimal parameter4,
      java.lang.String parameter5,
      java.lang.Boolean parameter6,
      long parameter7) {
    return jdbc.update(
        "INSERT INTO"
            + " runrun_v2.service_points(code,name,latitude,longitude,coordinate_system,enabled,config_version)"
            + " VALUES(?,?,?,?,?,?,?) ON CONFLICT(code) DO UPDATE SET"
            + " name=excluded.name,latitude=excluded.latitude,longitude=excluded.longitude,coordinate_system=excluded.coordinate_system,enabled=excluded.enabled,config_version=excluded.config_version,version=service_points.version+1,updated_at=now()",
        parameter1,
        parameter2,
        parameter3,
        parameter4,
        parameter5,
        parameter6,
        parameter7);
  }

  public int patchWritePlatformSettings(boolean enabled, java.lang.Object owner) {
    return jdbc.update(
        "UPDATE runrun_v2.platform_settings SET"
            + " new_order_enabled=?,review_owner_id=?,config_version=config_version+1,version=version+1,updated_at=now()"
            + " WHERE id=1",
        enabled,
        owner);
  }

  public int patchWriteAdminAuditLogs(
      java.lang.Long parameter1,
      java.lang.String key,
      java.lang.String parameter3,
      java.lang.String parameter4) {
    return jdbc.update(
        "INSERT INTO"
            + " runrun_v2.admin_audit_logs(administrator_id,action,target_type,target_id,request_id,reason,metadata)"
            + " VALUES(?,'SERVICE_CONFIG_CHANGED','SERVICE_CONFIG',1,?,?,?::jsonb)",
        parameter1,
        key,
        parameter3,
        parameter4);
  }

  public java.lang.Boolean requireOwnerValueAppUsers(java.lang.Object id) {
    return jdbc.queryForObject(
        "SELECT EXISTS(SELECT 1 FROM runrun_v2.app_users WHERE id=? AND role='ADMIN' AND"
            + " status='ACTIVE' AND v2_admin_enabled AND NOT controlled_identity)",
        Boolean.class,
        id);
  }
}
