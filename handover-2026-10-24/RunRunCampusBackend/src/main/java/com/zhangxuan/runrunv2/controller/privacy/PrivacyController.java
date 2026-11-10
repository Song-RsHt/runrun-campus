package com.zhangxuan.runrunv2.controller.privacy;

import com.zhangxuan.runrunv2.dto.privacy.PrivacyConsentRequest;
import com.zhangxuan.runrunv2.security.IdentityPermission;
import com.zhangxuan.runrunv2.security.IdentityWebConfiguration;
import com.zhangxuan.runrunv2.service.privacy.PrivacyService;
import com.zhangxuan.runrunv2.vo.privacy.PrivacyPolicyVO;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** v2 协议入口；仅只读协议允许匿名，记录同意必须验证当前会话。 */
@RestController
@RequestMapping("/api/v2/privacy")
public class PrivacyController {
    private final PrivacyService privacy;

    public PrivacyController(PrivacyService privacy) {
        this.privacy = privacy;
    }

    @GetMapping("/policy")
    @IdentityPermission(anonymous = true)
    public PrivacyPolicyVO policy() {
        return privacy.policy();
    }

    @PostMapping("/consent")
    @IdentityPermission
    public Map<String, Object> consent(
            HttpServletRequest request, @RequestBody PrivacyConsentRequest body) {
        privacy.consent(IdentityWebConfiguration.bearer(request), body.version());
        return Map.of("accepted", true, "version", body.version());
    }
}
