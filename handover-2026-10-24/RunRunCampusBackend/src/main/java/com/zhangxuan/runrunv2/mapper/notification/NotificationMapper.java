package com.zhangxuan.runrunv2.mapper.notification;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * SQL for NotificationService. Uses the caller's transaction-bound JDBC connection; never opens or
 * commits a transaction.
 */
@Repository
public class NotificationMapper {

  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  public NotificationMapper(org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> listRowsUserNotifications(long user) {
    return jdbc.queryForList(
        "SELECT id,order_id,type,content,created_at,read_at FROM runrun_v2.user_notifications WHERE"
            + " user_id=? ORDER BY created_at DESC,id DESC LIMIT 100",
        user);
  }

  public int markReadWriteUserNotifications(long id, long user) {
    return jdbc.update(
        "UPDATE runrun_v2.user_notifications SET read_at=COALESCE(read_at,now()) WHERE id=? AND"
            + " user_id=?",
        id,
        user);
  }

  public List<Map<String, Object>> claimRowsClaimNotificationOutbox(int max) {
    return jdbc.queryForList("SELECT * FROM runrun_v2.claim_notification_outbox(?)", max);
  }

  public Object completeDelivery(long id, boolean success, String error) {
    return jdbc.queryForObject(
        "SELECT runrun_v2.complete_notification_outbox(?,?,?)", Object.class, id, success, error);
  }
}
