package com.zhangxuan.runrunv2.mapper.payment;

import static com.zhangxuan.runrunv2.dto.response.payment.PaymentModels.*;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * SQL for MockPaymentAdapter. Uses the caller's transaction-bound JDBC connection; never opens or
 * commits a transaction.
 */
@Repository
public class MockPaymentMapper {

  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  public MockPaymentMapper(org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public int submitWriteMockChannelResults(String resourceType, Long resourceId) {
    return jdbc.update(
        "INSERT INTO runrun_v2.mock_channel_results(resource_type,resource_id,result)"
            + " VALUES(?,?,'PROCESSING')",
        resourceType,
        resourceId);
  }

  public List<Map<String, Object>> queryRowsMockChannelResults(String type, Long id) {
    return jdbc.queryForList(
        "SELECT result FROM runrun_v2.mock_channel_results WHERE resource_type=? AND resource_id=?",
        type,
        id);
  }

  public Long revisionValueMockChannelResults(String type, Long id) {
    return jdbc.queryForObject(
        "SELECT version FROM runrun_v2.mock_channel_results WHERE resource_type=? AND"
            + " resource_id=?",
        Long.class,
        type,
        id);
  }

  public int recordWriteMockChannelResults(String status, String type, Long id) {
    return jdbc.update(
        "UPDATE runrun_v2.mock_channel_results SET result=?,version=version+1 WHERE resource_type=?"
            + " AND resource_id=? AND result<>'SUCCESS'",
        status,
        type,
        id);
  }

  public int retryRefundWriteMockChannelResults(Long id) {
    return jdbc.update(
        "UPDATE runrun_v2.mock_channel_results SET result='PROCESSING',version=version+1 WHERE"
            + " resource_type='REFUND' AND resource_id=? AND result='FAILED'",
        id);
  }
}
