package com.zhangxuan.runruncampus.service.impl;

import com.zhangxuan.runruncampus.enums.OrderProofType;
import com.zhangxuan.runruncampus.exception.BusinessFailure;
import com.zhangxuan.runruncampus.integration.storage.OrderProofStorage;
import com.zhangxuan.runruncampus.model.ProofContent;
import com.zhangxuan.runruncampus.repository.OrderProofRepository;
import com.zhangxuan.runruncampus.repository.OrderRepository;
import com.zhangxuan.runruncampus.service.AdminOrderDeliveryProofService;
import java.io.IOException;
import org.springframework.stereotype.Service;
























/**
 * Reads private delivery evidence only after the controller has checked the existing admin
 * permission.
 */
@Service
public class AdminOrderDeliveryProofServiceImpl implements AdminOrderDeliveryProofService {

    private final OrderRepository orders;

    private final OrderProofRepository proofs;

    private final OrderProofStorage storage;

    public AdminOrderDeliveryProofServiceImpl(OrderRepository orders, OrderProofRepository proofs, OrderProofStorage storage) {
        this.orders = orders;
        this.proofs = proofs;
        this.storage = storage;
    }

    @Override
    public ProofContent read(Long id) {
        var order = orders.findById(id).orElseThrow(() -> new BusinessFailure(404, "订单不存在"));
        var proof = proofs.findByOrderAndType(order, OrderProofType.DELIVERY).orElseThrow(() -> new BusinessFailure(404, "送达凭证不存在"));
        if (!proof.getContentType().equals("image/jpeg") && !proof.getContentType().equals("image/png") && !proof.getContentType().equals("image/webp")) {
            throw new BusinessFailure(500, "送达凭证类型无效");
        }
        try {
            return new ProofContent(proof.getContentType(), storage.read(proof.getStoredFileName()));
        } catch (IOException exception) {
            throw new BusinessFailure(500, "送达凭证读取失败", exception);
        }
    }
}
