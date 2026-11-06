package com.zhangxuan.runruncampus.controller;

import com.zhangxuan.runruncampus.dto.AccountDeletionRequest;
import com.zhangxuan.runruncampus.dto.ConsentRequest;
import com.zhangxuan.runruncampus.service.AuthService;
import com.zhangxuan.runruncampus.service.PrivacyService;
import com.zhangxuan.runruncampus.vo.PrivacyConsentVO;
import com.zhangxuan.runruncampus.vo.PrivacyPolicyVO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

















@RestController
@RequestMapping("/api/v1/privacy")
public class PrivacyController {

    private final AuthService auth;

    private final PrivacyService privacy;

    public PrivacyController(AuthService auth, PrivacyService privacy) {
        this.auth = auth;
        this.privacy = privacy;
    }

    @GetMapping("/policy")
    public PrivacyPolicyVO policy() {
        return privacy.policy();
    }

    @GetMapping("/consent")
    public PrivacyConsentVO consentStatus(@RequestHeader("Authorization") String authorization) {
        return privacy.status(auth.requireUser(authorization));
    }

    @PostMapping("/consent")
    public PrivacyConsentVO consent(@RequestHeader("Authorization") String authorization, @Valid @RequestBody ConsentRequest request) {
        return privacy.consent(auth.requireUser(authorization), request.version());
    }

    @PostMapping("/withdrawal")
    public ResponseEntity<Void> withdrawConsent(@RequestHeader("Authorization") String authorization) {
        privacy.withdrawConsent(auth.requireUser(authorization));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/account-deletion")
    public ResponseEntity<Void> deleteAccount(@RequestHeader("Authorization") String authorization, @Valid @RequestBody AccountDeletionRequest request) {
        privacy.deleteAccount(auth.requireUser(authorization), request.password(), request.confirmed());
        return ResponseEntity.noContent().build();
    }
}
