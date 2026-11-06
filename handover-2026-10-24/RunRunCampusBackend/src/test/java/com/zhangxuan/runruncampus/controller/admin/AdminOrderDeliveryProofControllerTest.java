package com.zhangxuan.runruncampus.controller.admin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.zhangxuan.runruncampus.controller.AdminOrderDeliveryProofController;
import com.zhangxuan.runruncampus.entity.CampusOrder;
import com.zhangxuan.runruncampus.entity.OrderProof;
import com.zhangxuan.runruncampus.enums.AdminPermission;
import com.zhangxuan.runruncampus.enums.OrderProofType;
import com.zhangxuan.runruncampus.exception.BusinessFailure;
import com.zhangxuan.runruncampus.integration.storage.OrderProofStorage;
import com.zhangxuan.runruncampus.repository.OrderProofRepository;
import com.zhangxuan.runruncampus.repository.OrderRepository;
import com.zhangxuan.runruncampus.service.AuthService;
import com.zhangxuan.runruncampus.service.impl.AdminOrderDeliveryProofServiceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.Optional;

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
                .thenThrow(new BusinessFailure(HttpStatus.FORBIDDEN.value()));
        var error =
                assertThrows(
                        BusinessFailure.class,
                        () -> controller().deliveryProof("Bearer token", 11L));
        assertEquals(HttpStatus.FORBIDDEN.value(), error.status());
        verifyNoInteractions(orders, proofs, storage);
    }

    @Test
    void authorizedAdminReceivesNoStoreImage() throws IOException {
        when(orders.findById(11L)).thenReturn(Optional.of(order));
        when(proofs.findByOrderAndType(order, OrderProofType.DELIVERY))
                .thenReturn(Optional.of(proof));
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
        when(proofs.findByOrderAndType(order, OrderProofType.DELIVERY))
                .thenReturn(Optional.of(proof));
        when(proof.getContentType()).thenReturn("text/html");
        var error =
                assertThrows(
                        BusinessFailure.class,
                        () -> controller().deliveryProof("Bearer token", 11L));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), error.status());
        verifyNoInteractions(storage);
    }

    private AdminOrderDeliveryProofController controller() {
        return new AdminOrderDeliveryProofController(
                auth, new AdminOrderDeliveryProofServiceImpl(orders, proofs, storage));
    }
}
