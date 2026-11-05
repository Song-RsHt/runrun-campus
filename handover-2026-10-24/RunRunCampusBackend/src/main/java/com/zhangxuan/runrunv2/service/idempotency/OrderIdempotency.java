package com.zhangxuan.runrunv2.service.idempotency;
import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.security.IdentitySecrets;
import com.zhangxuan.runrunv2.service.identity.IdentityService;
import com.zhangxuan.runrunv2.config.OrderJson;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderIdempotency {
    private final JdbcTemplate db;
    private final IdentitySecrets secrets;
    private final OrderJson json;
    public OrderIdempotency(JdbcTemplate db, IdentitySecrets secrets, OrderJson json) { this.db=db; this.secrets=secrets; this.json=json; }
    public <T> T replay(String actor, String scope, String key, Object content, Class<T> response) {
        IdentityService.text(key, 128);
        String hash = secrets.hash("write:"+scope, json.write(content));
        // The caller holds its actor row lock, serializing even requests from other instances.
        List<Map<String,Object>> rows = db.queryForList("SELECT request_hash,response_snapshot,status FROM runrun_v2.idempotency_requests WHERE actor_id=? AND scope=? AND key=? FOR UPDATE", Long.valueOf(actor), scope, key);
        if (!rows.isEmpty()) {
            Map<String,Object> row = rows.get(0);
            if (!hash.equals(row.get("request_hash"))) throw new IdentityFailure(409,"IDEMPOTENCY_KEY_CONFLICT");
            if (!"SUCCEEDED".equals(row.get("status")) || row.get("response_snapshot")==null) throw new IdentityFailure(409,"ORDER_STATE_CONFLICT");
            return json.read(row.get("response_snapshot").toString(),response);
        }
        db.update("INSERT INTO runrun_v2.idempotency_requests(actor_id,scope,key,request_hash,status) VALUES(?,?,?,?,'PROCESSING')",Long.valueOf(actor),scope,key,hash);
        return null;
    }
    public void finish(String actor, String scope, String key, String resourceType, String resourceId, Object response) {
        db.update("UPDATE runrun_v2.idempotency_requests SET status='SUCCEEDED',resource_type=?,resource_id=?,response_snapshot=?::jsonb,updated_at=now() WHERE actor_id=? AND scope=? AND key=?",
                resourceType, resourceId, json.write(response), Long.valueOf(actor), scope, key);
    }
}
