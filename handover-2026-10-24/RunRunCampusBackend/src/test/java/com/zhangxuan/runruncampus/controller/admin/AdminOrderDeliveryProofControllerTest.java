package com.zhangxuan.runruncampus.controller.admin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.zhangxuan.runruncampus.enums.admin.AdminPermission;
import com.zhangxuan.runruncampus.enums.order.OrderProofType;
import com.zhangxuan.runruncampus.integration.storage.OrderProofStorage;
import com.zhangxuan.runruncampus.model.order.CampusOrder;
import com.zhangxuan.runruncampus.model.order.OrderProof;
import com.zhangxuan.runruncampus.repository.order.OrderProofRepository;
import com.zhangxuan.runruncampus.repository.order.OrderRepository;
import com.zhangxuan.runruncampus.service.admin.AdminOrderDeliveryProofService;
import com.zhangxuan.runruncampus.service.auth.AuthService;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AdminOrderDeliveryProofControllerTest {

  @Mock AuthService auth;

  @Mock OrderRepository orders;

  @Mock OrderProofRepository proofs;

  @Mock OrderProofStorage storage;

  @Mock CampusOrder order;

  @Mock OrderProof proof;

  @Test
  void restrictedAdminCannotReadDeliveryImage() {
    when(auth.requirePermission("Bearer token", AdminPermission.ORDER_FORCE_COMPLETE))
        .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
    var error =
        assertThrows(
            ResponseStatusException.class, () -> controller().deliveryProof("Bearer token", 11L));
    assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    verifyNoInteractions(orders, proofs, storage);
  }

  @Test
  void authorizedAdminReceivesNoStoreImage() throws IOException {
    when(orders.findById(11L)).thenReturn(Optional.of(order));
    when(proofs.findByOrderAndType(order, OrderProofType.DELIVERY)).thenReturn(Optional.of(proof));
    when(proof.getContentType()).thenReturn("image/png");
    when(proof.getStoredFileName()).thenReturn("11-delivery.png");
    when(storage.read("11-delivery.png")).thenReturn(new byte[] {1, 2, 3});
    var result = controller().deliveryProof("Bearer token", 11L);
    assertArrayEquals(new byte[] {1, 2, 3}, result.getBody());
    assertEquals("no-store", result.getHeaders().getCacheControl());
    assertEquals("nosniff", result.getHeaders().getFirst("X-Content-Type-Options"));
  }

  @Test
  void nonImageContentTypeIsNotServed() {
    when(orders.findById(11L)).thenReturn(Optional.of(order));
    when(proofs.findByOrderAndType(order, OrderProofType.DELIVERY)).thenReturn(Optional.of(proof));
    when(proof.getContentType()).thenReturn("text/html");
    var error =
        assertThrows(
            ResponseStatusException.class, () -> controller().deliveryProof("Bearer token", 11L));
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, error.getStatusCode());
    verifyNoInteractions(storage);
  }

  private AdminOrderDeliveryProofController controller() {
    return new AdminOrderDeliveryProofController(
        auth, new AdminOrderDeliveryProofService(orders, proofs, storage));
  }
}
