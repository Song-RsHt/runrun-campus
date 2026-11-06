package com.zhangxuan.runruncampus.controller;

import com.zhangxuan.runruncampus.service.AuthService;
import com.zhangxuan.runruncampus.service.CreditService;
import com.zhangxuan.runruncampus.vo.CreditProfileVO;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 信用分及风险信号 HTTP 入口；绑定请求并委托业务服务，不直接访问数据库。 */
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
    public CreditProfileVO mine(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return credit.profile(auth.requireUser(authorization), page, size);
    }
}
