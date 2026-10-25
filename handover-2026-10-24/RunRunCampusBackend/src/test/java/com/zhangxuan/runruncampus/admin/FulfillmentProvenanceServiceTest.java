package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.order.CampusOrder;
import com.zhangxuan.runruncampus.order.OrderPaymentMethod;
import com.zhangxuan.runruncampus.order.OrderRepository;
import com.zhangxuan.runruncampus.order.OrderStatus;
import com.zhangxuan.runruncampus.user.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FulfillmentProvenanceServiceTest {
    @Mock JdbcTemplate jdbc;
    @Mock OrderRepository orders;

    private FulfillmentProvenanceService service() {
        return new FulfillmentProvenanceService(jdbc, orders);
    }

    @Test
    void rolesSeparateRecordingFromVerificationAndDoNotGrantSupportAccess() {
        assertEquals(true, AdminRole.OPERATIONS.permits(AdminPermission.PROVENANCE_RECORD));
        assertEquals(false, AdminRole.OPERATIONS.permits(AdminPermission.PROVENANCE_VERIFY));
        assertEquals(true, AdminRole.AUDITOR.permits(AdminPermission.PROVENANCE_VERIFY));
        assertEquals(false, AdminRole.AUDITOR.permits(AdminPermission.PROVENANCE_RECORD));
        assertEquals(false, AdminRole.SUPPORT.permits(AdminPermission.PROVENANCE_VIEW));
    }

    @Test
    void moneyWithoutIndependentEvidenceIsRejectedBeforeDatabaseAccess() {
        var request = new FulfillmentProvenanceService.CreateRequest("manual-001",
                FulfillmentProvenanceService.Channel.MANUAL_OFFLINE, null, Instant.parse("2026-10-15T00:00:00Z"),
                null, 20000L, null, "a".repeat(64), null);

        var error = assertThrows(ResponseStatusException.class, () -> service().record(admin(1), request));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(jdbc, orders);
    }

    @Test
    void endToEndClaimWithoutAppOrderIsRejected() {
        var request = new FulfillmentProvenanceService.CreateRequest("app-001",
                FulfillmentProvenanceService.Channel.APP_END_TO_END, null,
                Instant.parse("2026-10-15T00:00:00Z"), null, null, null, "a".repeat(64), null);

        assertThrows(ResponseStatusException.class, () -> service().record(admin(1), request));
        verifyNoInteractions(jdbc, orders);
    }

    @Test
    void cashPaymentCannotBeClassifiedAsSoftwareEndToEnd() {
        CampusOrder order = org.mockito.Mockito.mock(CampusOrder.class);
        when(order.getStatus()).thenReturn(OrderStatus.COMPLETED);
        when(order.getPaymentMethod()).thenReturn(OrderPaymentMethod.CASH_ON_DELIVERY);
        when(orders.findById(17L)).thenReturn(Optional.of(order));
        var request = new FulfillmentProvenanceService.CreateRequest("app-017",
                FulfillmentProvenanceService.Channel.APP_END_TO_END, 17L,
                Instant.parse("2026-10-15T00:00:00Z"), null, null, null, "a".repeat(64), null);

        var error = assertThrows(ResponseStatusException.class, () -> service().record(admin(1), request));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(jdbc);
    }

    @Test
    void verifierCannotApproveOwnEntry() {
        when(jdbc.update(any(String.class), eq(1L), eq(7L), eq(1L))).thenReturn(0);

        var error = assertThrows(ResponseStatusException.class, () -> service().verify(admin(1), 7));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(jdbc).update(any(String.class), eq(1L), eq(7L), eq(1L));
    }

    private AppUser admin(long id) {
        AppUser user = new AppUser("10000000000", "hash", "管理员", com.zhangxuan.runruncampus.user.UserRole.ADMIN);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
