package com.zhangxuan.runruncampus.mapper;

import com.zhangxuan.runruncampus.enums.Channel;
import com.zhangxuan.runruncampus.vo.FulfillmentProvenanceEntryVO;
import com.zhangxuan.runruncampus.vo.FulfillmentProvenanceSummaryVO;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/** 履约来源及财务凭证登记数据访问；使用调用方事务绑定的 JDBC 连接，不开启或提交事务。 */
@Repository
public class FulfillmentProvenanceMapper {

    private final JdbcTemplate jdbc;

    public FulfillmentProvenanceMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Long insertProvenance(
            String sourceRef,
            String channel,
            Long appOrderId,
            Timestamp completedAt,
            Long customerPaidCents,
            Long platformRevenueCents,
            Long courierSettlementCents,
            String completionEvidenceSha256,
            String financialEvidenceSha256,
            Long recordedBy) {
        return jdbc.queryForObject(
                """
                insert into fulfillment_provenance
                (source_ref, channel, app_order_id, completed_at, customer_paid_cents,
                 platform_revenue_cents, courier_settlement_cents, completion_evidence_sha256,
                 financial_evidence_sha256, recorded_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id
                """,
                Long.class,
                sourceRef,
                channel,
                appOrderId,
                completedAt,
                customerPaidCents,
                platformRevenueCents,
                courierSettlementCents,
                completionEvidenceSha256,
                financialEvidenceSha256,
                recordedBy);
    }

    public int verifyByIndependentAdministrator(
            Long verifierId, long provenanceId, Long excludedRecorderId) {
        return jdbc.update(
                """
                update fulfillment_provenance set verified_by = ?, verified_at = now()
                where id = ? and verified_by is null and recorded_by <> ?
                """,
                verifierId,
                provenanceId,
                excludedRecorderId);
    }

    public List<FulfillmentProvenanceEntryVO> pageProvenance(int offset) {
        return jdbc.query(
                """
                select * from fulfillment_provenance order by id desc limit 100 offset ?
                """,
                (rs, row) -> map(rs),
                offset);
    }

    public FulfillmentProvenanceSummaryVO summarizeVerifiedProvenance() {
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
                        new FulfillmentProvenanceSummaryVO(
                                rs.getLong("recorded_count"),
                                rs.getLong("verified_count"),
                                rs.getLong("verified_revenue_count"),
                                rs.getBigDecimal("verified_revenue_cents") == null
                                        ? null
                                        : rs.getBigDecimal("verified_revenue_cents")
                                                .longValueExact()));
    }

    public FulfillmentProvenanceEntryVO findProvenance(long id) {
        return jdbc.queryForObject(
                """
                select *
                from fulfillment_provenance
                where id = ?
                """,
                (rs, row) -> map(rs),
                id);
    }

    private static FulfillmentProvenanceEntryVO map(ResultSet rs) throws SQLException {
        return new FulfillmentProvenanceEntryVO(
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
                rs.getTimestamp("verified_at") == null
                        ? null
                        : rs.getTimestamp("verified_at").toInstant());
    }
}
