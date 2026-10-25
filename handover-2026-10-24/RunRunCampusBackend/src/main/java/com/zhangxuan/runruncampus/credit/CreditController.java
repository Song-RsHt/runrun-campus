package com.zhangxuan.runruncampus.credit;

import com.zhangxuan.runruncampus.auth.AuthService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/credit")
public class CreditController {
    private final AuthService auth;
    private final CreditService credit;

    public CreditController(AuthService auth, CreditService credit) {
        this.auth = auth;
        this.credit = credit;
    }

    @GetMapping("/me")
    CreditService.ProfileResponse mine(@RequestHeader("Authorization") String authorization,
                                       @RequestParam(defaultValue = "0") @Min(0) int page,
                                       @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return credit.profile(auth.requireUser(authorization), page, size);
    }
}
