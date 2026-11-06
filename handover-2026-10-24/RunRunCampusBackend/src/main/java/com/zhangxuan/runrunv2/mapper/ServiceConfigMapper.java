package com.zhangxuan.runrunv2.mapper;

import com.zhangxuan.runrunv2.vo.DeliveryZoneVO;
import com.zhangxuan.runrunv2.vo.ServicePointVO;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 新系统服务配置及计价数据访问；使用调用方事务绑定的 JDBC 连接，不开启或提交事务。 */
@Repository
public class ServiceConfigMapper {

    private final JdbcTemplate jdbc;

    public ServiceConfigMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> lockSettings(boolean forUpdate) {
        return jdbc.queryForMap(
                """
                SELECT *
                FROM runrun_v2.platform_settings
                WHERE id = 1
                FOR
                """
                        + (forUpdate
                                ? """
                                    UPDATE
                                    """
                                : "SHARE"));
    }

    public List<DeliveryZoneVO> findZones(boolean includeDisabled) {
        return jdbc.query(
                """
                SELECT *
                FROM runrun_v2.delivery_zones
                """
                        + (includeDisabled
                                ? ""
                                : """
                                    WHERE enabled
                                    """)
                        + """
                            \sORDER BY id
                            """,
                (r, n) ->
                        new DeliveryZoneVO(
                                r.getString("id"),
                                r.getString("code"),
                                r.getString("name"),
                                r.getLong("package_surcharge_cents"),
                                r.getBoolean("enabled"),
                                r.getLong("config_version"),
                                r.getLong("version")));
    }

    public List<ServicePointVO> findServicePoints(boolean includeDisabled) {
        return jdbc.query(
                """
                SELECT *
                FROM runrun_v2.service_points
                """
                        + (includeDisabled
                                ? ""
                                : """
                                    WHERE enabled
                                    """)
                        + """
                            \sORDER BY id
                            """,
                (r, n) ->
                        new ServicePointVO(
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

    public List<Map<String, Object>> findEnabledZone(String code) {
        return findEnabled("delivery_zones", code);
    }

    public List<Map<String, Object>> findEnabledServicePoint(String code) {
        return findEnabled("service_points", code);
    }

    private List<Map<String, Object>> findEnabled(String table, String code) {
        return jdbc.queryForList(
                """
                SELECT *
                FROM runrun_v2.\
                """
                        + table
                        + """
                            \sWHERE code = ?
                                AND enabled
                            """,
                code);
    }

    public int upsertZone(
            String code,
            String name,
            Long packageSurchargeCents,
            Boolean enabled,
            long configVersion) {
        return jdbc.update(
                """
                INSERT INTO runrun_v2.delivery_zones (
                    code,
                    name,
                    package_surcharge_cents,
                    enabled,
                    config_version
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    ?,
                    ?
                )
                ON CONFLICT (
                    code
                ) DO UPDATE
                SET name = excluded.name,
                    package_surcharge_cents = excluded.package_surcharge_cents,
                    enabled = excluded.enabled,
                    config_version = excluded.config_version,
                    version = delivery_zones.version + 1,
                    updated_at = now (

                )
                """,
                code,
                name,
                packageSurchargeCents,
                enabled,
                configVersion);
    }

    public int upsertServicePoint(
            String code,
            String name,
            BigDecimal latitude,
            BigDecimal longitude,
            String coordinateSystem,
            Boolean enabled,
            long configVersion) {
        return jdbc.update(
                """
                INSERT INTO runrun_v2.service_points (
                    code,
                    name,
                    latitude,
                    longitude,
                    coordinate_system,
                    enabled,
                    config_version
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?
                )
                ON CONFLICT (
                    code
                ) DO UPDATE
                SET name = excluded.name,
                    latitude = excluded.latitude,
                    longitude = excluded.longitude,
                    coordinate_system = excluded.coordinate_system,
                    enabled = excluded.enabled,
                    config_version = excluded.config_version,
                    version = service_points.version + 1,
                    updated_at = now (

                )
                """,
                code,
                name,
                latitude,
                longitude,
                coordinateSystem,
                enabled,
                configVersion);
    }

    public int updateSettings(boolean enabled, Long reviewOwnerId) {
        return jdbc.update(
                """
                UPDATE runrun_v2.platform_settings
                SET new_order_enabled = ?,
                    review_owner_id = ?,
                    config_version = config_version + 1,
                    version = version + 1,
                    updated_at = now()
                WHERE id = 1
                """,
                enabled,
                reviewOwnerId);
    }

    public int insertConfigChangeAudit(
            Long administratorId, String requestId, String reason, String metadata) {
        return jdbc.update(
                """
                INSERT INTO runrun_v2.admin_audit_logs (
                    administrator_id,
                    action,
                    target_type,
                    target_id,
                    request_id,
                    reason,
                    metadata
                )
                VALUES (
                    ?,
                    'SERVICE_CONFIG_CHANGED',
                    'SERVICE_CONFIG',
                    1,
                    ?,
                    ?,
                    ?::jsonb
                )
                """,
                administratorId,
                requestId,
                reason,
                metadata);
    }

    public Boolean isEligibleReviewOwner(Long userId) {
        return jdbc.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM runrun_v2.app_users
                    WHERE id = ?
                        AND role = 'ADMIN'
                        AND status = 'ACTIVE'
                        AND v2_admin_enabled
                        AND NOT controlled_identity)
                """,
                Boolean.class,
                userId);
    }
}
