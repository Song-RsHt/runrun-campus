package com.zhangxuan.runruncampus.privacy;

import com.zhangxuan.runruncampus.auth.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    PrivacyService.Policy policy() {
        return privacy.policy();
    }

    @GetMapping("/consent")
    PrivacyService.ConsentStatus consentStatus(@RequestHeader("Authorization") String authorization) {
        return privacy.status(auth.requireUser(authorization));
    }

    @PostMapping("/consent")
    PrivacyService.ConsentStatus consent(@RequestHeader("Authorization") String authorization,
                                         @Valid @RequestBody ConsentRequest request) {
        return privacy.consent(auth.requireUser(authorization), request.version());
    }

    @PostMapping("/withdrawal")
    ResponseEntity<Void> withdrawConsent(@RequestHeader("Authorization") String authorization) {
        privacy.withdrawConsent(auth.requireUser(authorization));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/account-deletion")
    ResponseEntity<Void> deleteAccount(@RequestHeader("Authorization") String authorization,
                                       @Valid @RequestBody AccountDeletionRequest request) {
        privacy.deleteAccount(auth.requireUser(authorization), request.password(), request.confirmed());
        return ResponseEntity.noContent().build();
    }

    public record ConsentRequest(@NotBlank @Size(max = 32) String version) { }
    public record AccountDeletionRequest(
            @NotBlank(message = "请输入当前密码") @Size(max = 72) String password,
            @AssertTrue(message = "请确认注销账号") boolean confirmed) { }
}
