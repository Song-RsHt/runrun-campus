package com.zhangxuan.runruncampus.mapper.admin;

import com.zhangxuan.runruncampus.dto.response.provenance.Entry;
import com.zhangxuan.runruncampus.dto.response.provenance.Summary;
import com.zhangxuan.runruncampus.enums.provenance.Channel;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.stereotype.Repository;

/**
 * SQL for FulfillmentProvenanceService. Uses the caller's transaction-bound JDBC connection; never
 * opens or commits a transaction.
 */
@Repository
public class FulfillmentProvenanceMapper {

  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  public FulfillmentProvenanceMapper(org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public java.lang.Long recordValueStatement(
      java.lang.String parameter2,
      java.lang.String parameter3,
      java.lang.Long parameter4,
      java.sql.Timestamp parameter5,
      java.lang.Long parameter6,
      java.lang.Long parameter7,
      java.lang.Long parameter8,
      java.lang.String parameter9,
      java.lang.Object parameter10,
      java.lang.Long parameter11) {
    return jdbc.queryForObject(
        """
        insert into fulfillment_provenance
        (source_ref, channel, app_order_id, completed_at, customer_paid_cents,
         platform_revenue_cents, courier_settlement_cents, completion_evidence_sha256,
         financial_evidence_sha256, recorded_by)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id
        """,
        Long.class,
        parameter2,
        parameter3,
        parameter4,
        parameter5,
        parameter6,
        parameter7,
        parameter8,
        parameter9,
        parameter10,
        parameter11);
  }

  public int verifyWriteStatement(java.lang.Long parameter1, long id, java.lang.Long parameter3) {
    return jdbc.update(
        """
        update fulfillment_provenance set verified_by = ?, verified_at = now()
        where id = ? and verified_by is null and recorded_by <> ?
        """,
        parameter1,
        id,
        parameter3);
  }

  public java.util.List<com.zhangxuan.runruncampus.dto.response.provenance.Entry> listRowsStatement(
      int parameter2) {
    return jdbc.query(
        """
        select * from fulfillment_provenance order by id desc limit 100 offset ?
        """,
        (rs, row) -> map(rs),
        parameter2);
  }

  public com.zhangxuan.runruncampus.dto.response.provenance.Summary summaryValueStatement() {
    return jdbc.queryForObject(
        """
select count(*) as recorded_count,
       count(*) filter (where verified_at is not null) as verified_count,
       count(*) filter (where verified_at is not null and platform_revenue_cents is not null)
           as verified_revenue_count,
       sum(platform_revenue_cents) filter
           (where verified_at is not null and platform_revenue_cents is not null)
           as verified_revenue_cents
from fulfillment_provenance
""",
        (rs, row) ->
            new Summary(
                rs.getLong("recorded_count"),
                rs.getLong("verified_count"),
                rs.getLong("verified_revenue_count"),
                rs.getBigDecimal("verified_revenue_cents") == null
                    ? null
                    : rs.getBigDecimal("verified_revenue_cents").longValueExact()));
  }

  public com.zhangxuan.runruncampus.dto.response.provenance.Entry findValueStatement(long id) {
    return jdbc.queryForObject(
        "select * from fulfillment_provenance where id = ?", (rs, row) -> map(rs), id);
  }

  private static Entry map(ResultSet rs) throws SQLException {
    return new Entry(
        rs.getLong("id"),
        rs.getString("source_ref"),
        Channel.valueOf(rs.getString("channel")),
        (Long) rs.getObject("app_order_id"),
        rs.getTimestamp("completed_at").toInstant(),
        (Long) rs.getObject("customer_paid_cents"),
        (Long) rs.getObject("platform_revenue_cents"),
        (Long) rs.getObject("courier_settlement_cents"),
        rs.getString("completion_evidence_sha256"),
        rs.getString("financial_evidence_sha256"),
        rs.getLong("recorded_by"),
        rs.getTimestamp("recorded_at").toInstant(),
        (Long) rs.getObject("verified_by"),
        rs.getTimestamp("verified_at") == null ? null : rs.getTimestamp("verified_at").toInstant());
  }
}
