package com.zhangxuan.runruncampus.v2.foundation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class ResourceAccess {
    private final JdbcTemplate jdbc;
    public ResourceAccess(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Only pass the actor extracted from the verified server session, never a request-body userId. */
    public long ownedOrder(long authenticatedActorId, long orderId) {
        var values = jdbc.queryForList("select version from v2_orders where id=? and user_id=?", Long.class, orderId, authenticatedActorId);
        ResourceChecks.visible(!values.isEmpty()); return values.get(0);
    }
    public long ownedCertification(long authenticatedCourierId, long certificationId) {
        var values = jdbc.queryForList("select version from courier_certifications where id=? and courier_id=?", Long.class, certificationId, authenticatedCourierId);
        ResourceChecks.visible(!values.isEmpty()); return values.get(0);
    }
    public void relatedTask(long authenticatedCourierId, long orderId, long taskId) {
        ResourceChecks.visible(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from fulfillment_tasks where id=? and order_id=? and courier_id=?)",
                Boolean.class, taskId, orderId, authenticatedCourierId)));
    }
    public void enabledAdministrator(long authenticatedAdminId, long sessionSecurityVersion) {
        List<java.util.Map<String,Object>> rows = jdbc.queryForList("select role,enabled,deleted_at,v2_admin_enabled,v2_admin_security_version from app_users where id=?", authenticatedAdminId);
        if (rows.isEmpty()) throw new V2Exception(V2Error.AUTHENTICATION_FAILED,"会话无效");
        var row=rows.get(0);
        if (!Boolean.TRUE.equals(row.get("enabled")) || row.get("deleted_at") != null)
            throw new V2Exception(V2Error.AUTHENTICATION_FAILED,"会话无效");
        if (!"ADMIN".equals(row.get("role"))) throw new V2Exception(V2Error.FORBIDDEN,"身份不允许该操作");
        // C C01: re-read on each request so an old session cannot survive a disable/re-enable cycle.
        if (!Boolean.TRUE.equals(row.get("v2_admin_enabled")) || ((Number)row.get("v2_admin_security_version")).longValue()!=sessionSecurityVersion)
            throw new V2Exception(V2Error.V2_ADMIN_NOT_ENABLED,"管理员资格已停用或会话已失效");
    }
}
