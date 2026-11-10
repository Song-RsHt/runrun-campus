package com.zhangxuan.runrunv2.controller.privacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhangxuan.runrunv2.service.impl.privacy.PrivacyServiceImpl;
import com.zhangxuan.runrunv2.vo.identity.IdentityAccountVO;

import org.junit.jupiter.api.Test;

/** 核对协议正文沿用既有批准版本，以及账号派生权限的真实 JSON。 */
class PrivacyPolicyContractTest {
    @Test
    void publishedPolicyMatchesApprovedSourceWithoutLegalEdits() {
        var policy = new PrivacyServiceImpl(null, null).policy();
        var approved =
                new com.zhangxuan.runruncampus.service.impl.privacy.PrivacyServiceImpl(
                                null, null, null, null, null, null, null, null, null)
                        .policy();
        assertEquals(approved.version(), policy.version());
        assertEquals(approved.title(), policy.title());
        assertEquals(approved.effectiveDate(), policy.effectiveDate());
        assertEquals(approved.operatorName(), policy.operatorName());
        assertEquals(approved.contact(), policy.contact());
        assertEquals(approved.content(), policy.content());
    }

    @Test
    void permissionsRequireActiveEnabledAdminAndAlwaysSerializeAsArray() throws Exception {
        var json = new ObjectMapper();
        for (var account :
                java.util.List.of(
                        new IdentityAccountVO("1", "USER", "ACTIVE", false, 0, false),
                        new IdentityAccountVO("2", "COURIER", "ACTIVE", false, 0, true),
                        new IdentityAccountVO("3", "ADMIN", "ACTIVE", false, 0, false),
                        new IdentityAccountVO("4", "ADMIN", "SUSPENDED", true, 0, false))) {
            var permissions = json.readTree(json.writeValueAsString(account)).get("permissions");
            assertTrue(permissions.isArray());
            assertTrue(permissions.isEmpty());
        }
    }
}
