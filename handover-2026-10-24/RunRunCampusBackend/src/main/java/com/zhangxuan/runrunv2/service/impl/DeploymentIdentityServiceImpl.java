package com.zhangxuan.runrunv2.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.mapper.DeploymentIdentityMapper;
import com.zhangxuan.runrunv2.model.AccessResult;
import com.zhangxuan.runrunv2.model.IdentityUser;
import com.zhangxuan.runrunv2.model.InputValidation;
import com.zhangxuan.runrunv2.service.DeploymentIdentityService;
import com.zhangxuan.runrunv2.service.IdentityService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

















/**
 * Only the host-local command calls this service; it has no HTTP credential or public bootstrap
 * route.
 */
@Service
public class DeploymentIdentityServiceImpl implements DeploymentIdentityService {

    private final DeploymentIdentityMapper mapper;

    private final IdentityService identity;

    private final ObjectMapper json;

    public DeploymentIdentityServiceImpl(DeploymentIdentityMapper mapper, IdentityService identity, ObjectMapper json) {
        this.mapper = mapper;
        this.identity = identity;
        this.json = json;
    }

    @Transactional
    @Override
    public String bootstrap(String reason, String confirmation, String requestId) {
        reason(reason, requestId);
        if (!"CREATE DEPLOYMENT IDENTITY".equals(confirmation))
            throw IdentityFailure.invalid();
        identity.lock("deployment-bootstrap");
        if (mapper.deploymentIdentityExists())
            throw IdentityFailure.conflict();
        Long id = mapper.insertDeploymentIdentity();
        audit(id, "V2_DEPLOYER_CREATED", id, requestId, reason, Map.of("actorKind", "CONTROLLED_DEPLOYMENT"));
        return id.toString();
    }

    @Transactional
    @Override
    public AccessResult provisionAdmin(long actorId, long userId, long expectedVersion, String reason, String confirmation, String requestId) {
        authorize(actorId);
        reason(reason, requestId);
        if (!("CREATE ADMIN " + userId).equals(confirmation))
            throw IdentityFailure.invalid();
        identity.lock("deployment-request:" + actorId + ":" + requestId);
        AccessResult replay = replay(actorId, "V2_ADMIN_CREATED", userId, false, expectedVersion, reason, requestId);
        if (replay != null)
            return replay;
        IdentityUser target = identity.user(userId, true);
        // ADMIN starts from a freshly Wechat-verified account, never a legacy phone lookup or anonymous
        // signup.
        if (!"USER".equals(target.role()) || !"ACTIVE".equals(target.status()) || target.controlled() || !mapper.hasWechatBinding(userId))
            throw IdentityFailure.conflict();
        checkVersion(target.securityVersion(), expectedVersion);
        long next = expectedVersion + 1;
        mapper.promoteToDisabledAdmin(next, userId);
        invalidate(userId);
        audit(actorId, "V2_ADMIN_CREATED", userId, requestId, reason, metadata(false, false, expectedVersion, next));
        return new AccessResult(Long.toString(userId), false, next);
    }

    @Transactional
    @Override
    public AccessResult changeAccess(long actorId, long userId, boolean enabled, long expectedVersion, String reason, String confirmation, String requestId) {
        // M47 requires replacement before disabling the configured review owner.
        // Lock before account rows, matching M02 configuration writes across instances.
        identity.lock("review-owner-policy");
        authorize(actorId);
        reason(reason, requestId);
        if (!((enabled ? "ENABLE " : "DISABLE ") + userId).equals(confirmation))
            throw IdentityFailure.invalid();
        identity.lock("deployment-request:" + actorId + ":" + requestId);
        AccessResult replay = replay(actorId, "V2_ADMIN_ACCESS_CHANGED", userId, enabled, expectedVersion, reason, requestId);
        if (replay != null)
            return replay;
        IdentityUser target = identity.user(userId, true);
        if (!"ADMIN".equals(target.role()) || target.controlled() || !"ACTIVE".equals(target.status()))
            throw new IdentityFailure(403, "FORBIDDEN");
        checkVersion(target.securityVersion(), expectedVersion);
        if (!enabled && Boolean.TRUE.equals(mapper.settingsInstalled()) && Boolean.TRUE.equals(mapper.isReviewOwner(userId))) {
            throw new IdentityFailure(409, "REVIEW_OWNER_REQUIRED");
        }
        long next = expectedVersion + 1;
        // Locking the account serializes grants with session issue; audit/version/revocation commit
        // together.
        mapper.updateAdminAccess(enabled, next, userId);
        invalidate(userId);
        audit(actorId, "V2_ADMIN_ACCESS_CHANGED", userId, requestId, reason, metadata(target.enabled(), enabled, expectedVersion, next));
        return new AccessResult(Long.toString(userId), enabled, next);
    }

    private void authorize(long actorId) {
        IdentityUser actor = identity.user(actorId, true);
        if (!actor.controlled() || !"ADMIN".equals(actor.role()) || !"ACTIVE".equals(actor.status()))
            throw new IdentityFailure(403, "ADMIN_ACCESS_MANAGEMENT_FORBIDDEN");
    }

    private void invalidate(long id) {
        mapper.invalidateAdminSessions(id);
    }

    private static void checkVersion(long actual, long expected) {
        if (expected < 0 || actual != expected)
            throw new IdentityFailure(409, "ORDER_STATE_CONFLICT");
    }

    private static void reason(String reason, String requestId) {
        InputValidation.text(reason, 1000);
        InputValidation.text(requestId, 128);
    }

    private Map<String, Object> metadata(boolean oldEnabled, boolean newEnabled, long oldVersion, long newVersion) {
        return Map.of("oldEnabled", oldEnabled, "newEnabled", newEnabled, "oldSecurityVersion", oldVersion, "newSecurityVersion", newVersion, "actorKind", "CONTROLLED_DEPLOYMENT");
    }

    private void audit(long actor, String action, long target, String requestId, String reason, Map<String, Object> metadata) {
        try {
            Map<String, Object> details = new LinkedHashMap<>(metadata);
            details.put("reason", reason);
            mapper.insertAccessAudit(actor, action, target, requestId, reason, json.writeValueAsString(details));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Audit encoding failed");
        }
    }

    private AccessResult replay(long actor, String action, long target, boolean enabled, long version, String reason, String requestId) {
        List<Map<String, Object>> rows = mapper.findAccessAudit(actor, action, requestId);
        if (rows.isEmpty())
            return null;
        try {
            Map<String, Object> row = rows.get(0);
            var data = json.readTree((String) row.get("data"));
            if (((Number) row.get("target_id")).longValue() != target || !reason.equals(row.get("reason")) || data.path("newEnabled").asBoolean() != enabled || data.path("oldSecurityVersion").asLong() != version)
                throw new IdentityFailure(409, "IDEMPOTENCY_KEY_CONFLICT");
            return new AccessResult(Long.toString(target), enabled, data.path("newSecurityVersion").asLong());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Audit decoding failed");
        }
    }
}
