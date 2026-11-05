package com.zhangxuan.runrunv2.mapper.payment;

import static com.zhangxuan.runrunv2.model.payment.PaymentModels.*;

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

  public int submitWriteMockChannelResults(java.lang.String type, java.lang.Long parameter2) {
    return jdbc.update(
        "INSERT INTO runrun_v2.mock_channel_results(resource_type,resource_id,result)"
            + " VALUES(?,?,'PROCESSING')",
        type,
        parameter2);
  }

  public java.util.List<java.util.Map<java.lang.String, java.lang.Object>>
      queryRowsMockChannelResults(java.lang.String type, java.lang.Long parameter2) {
    return jdbc.queryForList(
        "SELECT result FROM runrun_v2.mock_channel_results WHERE resource_type=? AND resource_id=?",
        type,
        parameter2);
  }

  public java.lang.Long revisionValueMockChannelResults(
      java.lang.String type, java.lang.Long parameter3) {
    return jdbc.queryForObject(
        "SELECT version FROM runrun_v2.mock_channel_results WHERE resource_type=? AND"
            + " resource_id=?",
        Long.class,
        type,
        parameter3);
  }

  public int recordWriteMockChannelResults(
      java.lang.String parameter1, java.lang.String type, java.lang.Long parameter3) {
    return jdbc.update(
        "UPDATE runrun_v2.mock_channel_results SET result=?,version=version+1 WHERE resource_type=?"
            + " AND resource_id=? AND result<>'SUCCESS'",
        parameter1,
        type,
        parameter3);
  }

  public int retryRefundWriteMockChannelResults(java.lang.Long parameter1) {
    return jdbc.update(
        "UPDATE runrun_v2.mock_channel_results SET result='PROCESSING',version=version+1 WHERE"
            + " resource_type='REFUND' AND resource_id=? AND result='FAILED'",
        parameter1);
  }
}
