package com.zhangxuan.runruncampus.controller;

import com.zhangxuan.runruncampus.enums.AdminPermission;
import com.zhangxuan.runruncampus.service.AdminOrderDeliveryProofService;
import com.zhangxuan.runruncampus.service.AuthService;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员送达凭证 HTTP 入口；绑定请求并委托业务服务，不直接访问数据库。 */
@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderDeliveryProofController {

    private final AuthService auth;
    private final AdminOrderDeliveryProofService service;

    public AdminOrderDeliveryProofController(
            AuthService auth, AdminOrderDeliveryProofService service) {
        this.auth = auth;
        this.service = service;
    }

    @GetMapping("/{id}/delivery-proof")
    public ResponseEntity<byte[]> deliveryProof(
            @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        auth.requirePermission(authorization, AdminPermission.ORDER_FORCE_COMPLETE);
        var proof = service.read(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(proof.contentType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(proof.bytes());
    }
}
