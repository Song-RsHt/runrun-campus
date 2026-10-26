package com.zhangxuan.runruncampus.v2.foundation;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.function.Supplier;

@Component
public class IdempotentWrites {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AggregateLocks locks;
    private final TransactionTemplate transaction;
    public IdempotentWrites(JdbcTemplate jdbc, ObjectMapper json, AggregateLocks locks, PlatformTransactionManager manager) {
        this.jdbc = jdbc; this.json = json; this.locks = locks; this.transaction = new TransactionTemplate(manager);
    }
    public record Result(String resourceType, String resourceId, JsonNode snapshot) {}

    /** C A01: authorization is repeated even on replay; expectedVersion belongs inside businessWrite. */
    public Result execute(long actorId, String scope, String key, JsonNode boundContent,
                          Collection<AggregateLocks.Subject> plan, Runnable authorize, Supplier<Result> businessWrite) {
        if (actorId <= 0 || scope == null || scope.isBlank() || key == null || key.isBlank() || boundContent == null)
            throw new V2Exception(V2Error.VALIDATION_ERROR, "幂等键及请求内容必填");
        String hash = hash(boundContent);
        return transaction.execute(tx -> {
            TransactionRequired.check();
            // Serialize the absent-row case too. Hashing the tuple avoids delimiter collisions.
            locks.advisory("idempotency:" + hash(json.valueToTree(List.of(actorId, scope, key))));
            locks.acquire(plan);
            authorize.run();
            var previous = jdbc.queryForList("select request_hash,status,resource_type,resource_id,response_snapshot::text from idempotency_requests where actor_id=? and scope=? and key=?",
                    actorId, scope, key);
            if (!previous.isEmpty()) {
                var row = previous.get(0);
                if (!hash.equals(row.get("request_hash")))
                    throw new V2Exception(V2Error.IDEMPOTENCY_KEY_CONFLICT, "同一幂等键的请求内容不同");
                if (!"COMPLETED".equals(row.get("status")))
                    throw new V2Exception(V2Error.ORDER_STATE_CONFLICT, "原请求尚无可重放结果");
                return new Result((String) row.get("resource_type"), (String) row.get("resource_id"), read((String) row.get("response_snapshot")));
            }
            Result result = Objects.requireNonNull(businessWrite.get());
            requireSafeSnapshot(result.snapshot());
            // No separately committed PROCESSING row: failure rolls back business, audit and key together.
            jdbc.update("insert into idempotency_requests(actor_id,scope,key,request_hash,status,resource_type,resource_id,response_snapshot) values (?,?,?,?,'COMPLETED',?,?,?::jsonb)",
                    actorId, scope, key, hash, result.resourceType(), result.resourceId(), result.snapshot().toString());
            return result;
        });
    }

    public String hash(JsonNode input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical(input).toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            ObjectNode output = json.createObjectNode();
            TreeSet<String> keys = new TreeSet<>(); node.fieldNames().forEachRemaining(keys::add);
            keys.forEach(k -> output.set(k, canonical(node.get(k)))); return output;
        }
        if (node.isArray()) {
            ArrayNode output = json.createArrayNode(); node.forEach(v -> output.add(canonical(v))); return output;
        }
        return node;
    }
    private JsonNode read(String value) {
        try { return json.readTree(value); }
        catch (Exception e) { throw new IllegalStateException("Unreadable idempotency snapshot"); }
    }
    private void requireSafeSnapshot(JsonNode node) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException("An explicit response DTO snapshot is required");
        inspect(node);
    }
    private void inspect(JsonNode node) {
        if (node.isObject()) node.fields().forEachRemaining(entry -> {
            String name = entry.getKey().replace("_", "").toLowerCase(Locale.ROOT);
            if (name.contains("password") || name.contains("token") || name.contains("secret") || name.contains("openid")
                    || name.contains("sessionkey") || name.contains("storagekey") || name.contains("url")
                    || name.equals("code") || name.equals("phonecode") || name.equals("logincode") || name.contains("ticket"))
                throw new IllegalArgumentException("Private fields must not enter an idempotency snapshot");
            inspect(entry.getValue());
        });
        else if (node.isArray()) node.forEach(this::inspect);
        else if (node.isTextual() && (node.textValue().contains("://") || node.textValue().startsWith("data:")))
            throw new IllegalArgumentException("Private access addresses must not enter an idempotency snapshot");
    }
}
