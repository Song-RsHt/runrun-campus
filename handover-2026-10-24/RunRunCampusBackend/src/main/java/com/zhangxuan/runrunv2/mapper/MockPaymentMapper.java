package com.zhangxuan.runrunv2.mapper;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * SQL for MockPaymentAdapter. Uses the caller's transaction-bound JDBC connection; never opens or
 * commits a transaction.
 */
@Repository
public class MockPaymentMapper {

    private final JdbcTemplate jdbc;

    public MockPaymentMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int insertProcessingResult(String resourceType, Long resourceId) {
        return jdbc.update("""
            INSERT INTO runrun_v2.mock_channel_results (
                resource_type,
                resource_id,
                result
            )
            VALUES (
                ?,
                ?,
                'PROCESSING'
            )
            """, resourceType, resourceId);
    }

    public List<Map<String, Object>> findResult(String resourceType, Long resourceId) {
        return jdbc.queryForList("""
            SELECT result
            FROM runrun_v2.mock_channel_results
            WHERE resource_type = ?
                AND resource_id = ?
            """, resourceType, resourceId);
    }

    public Long findResultVersion(String resourceType, Long resourceId) {
        return jdbc.queryForObject("""
            SELECT version
            FROM runrun_v2.mock_channel_results
            WHERE resource_type = ?
                AND resource_id = ?
            """, Long.class, resourceType, resourceId);
    }

    public int updateResultUnlessSuccessful(String result, String resourceType, Long resourceId) {
        return jdbc.update("""
            UPDATE runrun_v2.mock_channel_results
            SET result = ?,
                version = version + 1
            WHERE resource_type = ?
                AND resource_id = ?
                AND result <> 'SUCCESS'
            """, result, resourceType, resourceId);
    }

    public int retryFailedRefund(Long refundId) {
        return jdbc.update("""
            UPDATE runrun_v2.mock_channel_results
            SET result = 'PROCESSING',
                version = version + 1
            WHERE resource_type = 'REFUND'
                AND resource_id = ?
                AND result = 'FAILED'
            """, refundId);
    }
}
