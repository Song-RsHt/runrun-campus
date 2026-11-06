package com.zhangxuan.runrunv2.mapper;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;







/**
 * SQL for OrderIdempotency. Uses the caller's transaction-bound JDBC connection; never opens or
 * commits a transaction.
 */
@Repository
public class OrderIdempotencyMapper {

    private final JdbcTemplate jdbc;

    public OrderIdempotencyMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> findRequestForUpdate(Long actor, String scope, String key) {
        return jdbc.queryForList("""
            SELECT request_hash,
                response_snapshot,
                status
            FROM runrun_v2.idempotency_requests
            WHERE actor_id = ?
                AND scope = ?
                AND key = ?
            FOR UPDATE
            """, actor, scope, key);
    }

    public int insertRequest(Long actorId, String scope, String key, String requestHash) {
        return jdbc.update("""
            INSERT INTO runrun_v2.idempotency_requests (
                actor_id,
                scope,
                key,
                request_hash,
                status
            )
            VALUES (
                ?,
                ?,
                ?,
                ?,
                'PROCESSING'
            )
            """, actorId, scope, key, requestHash);
    }

    public int completeRequest(String resourceType, String resourceId, String responseJson, Long actor, String scope, String key) {
        return jdbc.update("""
            UPDATE runrun_v2.idempotency_requests
            SET status = 'SUCCEEDED',
                resource_type = ?,
                resource_id = ?,
                response_snapshot = ?::jsonb,
                updated_at = now()
            WHERE actor_id = ?
                AND scope = ?
                AND key = ?
            """, resourceType, resourceId, responseJson, actor, scope, key);
    }
}
