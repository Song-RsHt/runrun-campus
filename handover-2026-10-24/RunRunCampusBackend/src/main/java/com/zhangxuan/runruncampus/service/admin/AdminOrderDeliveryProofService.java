package com.zhangxuan.runruncampus.service.admin;

import com.zhangxuan.runruncampus.dto.response.admin.ProofContent;
import com.zhangxuan.runruncampus.enums.order.OrderProofType;
import com.zhangxuan.runruncampus.integration.storage.OrderProofStorage;
import com.zhangxuan.runruncampus.repository.order.OrderProofRepository;
import com.zhangxuan.runruncampus.repository.order.OrderRepository;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reads private delivery evidence only after the controller has checked the existing admin
 * permission.
 */
@Service
public class AdminOrderDeliveryProofService {
  private final OrderRepository orders;
  private final OrderProofRepository proofs;
  private final OrderProofStorage storage;

  public AdminOrderDeliveryProofService(
      OrderRepository orders, OrderProofRepository proofs, OrderProofStorage storage) {
    this.orders = orders;
    this.proofs = proofs;
    this.storage = storage;
  }

  public ProofContent read(Long id) {
    var order =
        orders
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
    var proof =
        proofs
            .findByOrderAndType(order, OrderProofType.DELIVERY)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "送达凭证不存在"));
    if (!proof.getContentType().equals("image/jpeg")
        && !proof.getContentType().equals("image/png")
        && !proof.getContentType().equals("image/webp")) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "送达凭证类型无效");
    }
    try {
      return new ProofContent(proof.getContentType(), storage.read(proof.getStoredFileName()));
    } catch (IOException exception) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "送达凭证读取失败", exception);
    }
  }
}
