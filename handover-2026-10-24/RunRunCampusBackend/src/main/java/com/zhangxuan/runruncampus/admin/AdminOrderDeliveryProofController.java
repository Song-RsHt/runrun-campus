package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.auth.AuthService;
import com.zhangxuan.runruncampus.order.OrderProofRepository;
import com.zhangxuan.runruncampus.order.OrderProofStorage;
import com.zhangxuan.runruncampus.order.OrderProofType;
import com.zhangxuan.runruncampus.order.OrderRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderDeliveryProofController {
    private final AuthService auth;
    private final OrderRepository orders;
    private final OrderProofRepository proofs;
    private final OrderProofStorage storage;

    public AdminOrderDeliveryProofController(AuthService auth, OrderRepository orders,
                                             OrderProofRepository proofs, OrderProofStorage storage) {
        this.auth = auth;
        this.orders = orders;
        this.proofs = proofs;
        this.storage = storage;
    }

    @GetMapping("/{id}/delivery-proof")
    ResponseEntity<byte[]> deliveryProof(@RequestHeader("Authorization") String authorization,
                                         @PathVariable Long id) {
        auth.requirePermission(authorization, AdminPermission.ORDER_FORCE_COMPLETE);
        var order = orders.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        var proof = proofs.findByOrderAndType(order, OrderProofType.DELIVERY)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "送达凭证不存在"));
        if (!proof.getContentType().equals("image/jpeg") && !proof.getContentType().equals("image/png")
                && !proof.getContentType().equals("image/webp")) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "送达凭证类型无效");
        }
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(proof.getContentType()))
                    .cacheControl(CacheControl.noStore())
                    .header("X-Content-Type-Options", "nosniff")
                    .body(storage.read(proof.getStoredFileName()));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "送达凭证读取失败", exception);
        }
    }
}
