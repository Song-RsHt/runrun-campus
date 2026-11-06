package com.zhangxuan.runrunv2.mapper.fulfillment;

import com.zhangxuan.runrunv2.model.fulfillment.TaskRow;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read projections with stable (created_at,id) pagination; no permission or cursor-token policy.
 */
@Repository
public class FulfillmentQueryMapper {

  private final JdbcTemplate jdbc;

  public FulfillmentQueryMapper(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<TaskRow> findCourierTasks(
      long courierId, String status, OffsetDateTime afterCreatedAt, Long afterId, int limit) {
    var parameters = new ArrayList<Object>();
    parameters.add(courierId);
    String where = "t.courier_id=?";
    if (status != null) {
      where += " AND t.status=?";
      parameters.add(status);
    }
    if (afterCreatedAt != null) {
      where += " AND (t.created_at,t.id)<(?,?)";
      parameters.add(afterCreatedAt);
      parameters.add(afterId);
    }
    parameters.add(limit);
    return jdbc.query(
        "SELECT t.*,o.order_no,o.status AS order_status,o.version AS order_version FROM"
            + " runrun_v2.fulfillment_tasks t JOIN runrun_v2.v2_orders o ON o.id=t.order_id WHERE "
            + where
            + " ORDER BY t.created_at DESC,t.id DESC LIMIT ?",
        (row, index) ->
            new TaskRow(
                row.getString("id"),
                row.getString("status"),
                row.getLong("version"),
                row.getString("order_id"),
                row.getString("order_no"),
                row.getString("order_status"),
                row.getLong("order_version"),
                row.getObject("created_at", OffsetDateTime.class),
                row.getObject("superseded_at") != null),
        parameters.toArray());
  }

  public List<Map<String, Object>> findPaidOrderPool(
      OffsetDateTime afterCreatedAt, Long afterId, int limit) {
    var parameters = new ArrayList<Object>();
    String where =
        "o.status='PENDING_ASSIGNMENT' AND EXISTS(SELECT 1 FROM runrun_v2.payment_orders p"
            + " WHERE p.order_id=o.id AND p.purpose='ORIGINAL' AND p.status='PAID')";
    if (afterCreatedAt != null) {
      where += " AND (o.created_at,o.id)<(?,?)";
      parameters.add(afterCreatedAt);
      parameters.add(afterId);
    }
    parameters.add(limit);
    return jdbc.queryForList(
        "SELECT o.* FROM runrun_v2.v2_orders o WHERE "
            + where
            + " ORDER BY o.created_at DESC,o.id DESC LIMIT ?",
        parameters.toArray());
  }
}
