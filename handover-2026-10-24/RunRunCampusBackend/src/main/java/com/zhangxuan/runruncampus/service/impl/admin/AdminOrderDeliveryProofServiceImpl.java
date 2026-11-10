package com.zhangxuan.runruncampus.service.impl.admin;

import com.zhangxuan.runruncampus.enums.order.OrderProofType;
import com.zhangxuan.runruncampus.exception.BusinessFailure;
import com.zhangxuan.runruncampus.integration.storage.OrderProofStorage;
import com.zhangxuan.runruncampus.model.admin.ProofContent;
import com.zhangxuan.runruncampus.repository.order.OrderProofRepository;
import com.zhangxuan.runruncampus.repository.order.OrderRepository;
import com.zhangxuan.runruncampus.service.admin.AdminOrderDeliveryProofService;

import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * 管理员送达凭证业务实现；通过注入的业务契约及数据访问组件执行，事务边界保留在本类。
 *
 * <p>管理员权限由入口检查后，才允许读取私有送达凭证。
 */
@Service
public class AdminOrderDeliveryProofServiceImpl implements AdminOrderDeliveryProofService {

    private final OrderRepository orders;
    private final OrderProofRepository proofs;
    private final OrderProofStorage storage;

    public AdminOrderDeliveryProofServiceImpl(
            OrderRepository orders, OrderProofRepository proofs, OrderProofStorage storage) {
        this.orders = orders;
        this.proofs = proofs;
        this.storage = storage;
    }

    @Override
    public ProofContent read(Long id) {
        var order = orders.findById(id).orElseThrow(() -> new BusinessFailure(404, "订单不存在"));
        var proof =
                proofs.findByOrderAndType(order, OrderProofType.DELIVERY)
                        .orElseThrow(() -> new BusinessFailure(404, "送达凭证不存在"));
        if (!proof.getContentType().equals("image/jpeg")
                && !proof.getContentType().equals("image/png")
                && !proof.getContentType().equals("image/webp")) {
            throw new BusinessFailure(500, "送达凭证类型无效");
        }
        try {
            return new ProofContent(
                    proof.getContentType(), storage.read(proof.getStoredFileName()));
        } catch (IOException exception) {
            throw new BusinessFailure(500, "送达凭证读取失败", exception);
        }
    }
}
