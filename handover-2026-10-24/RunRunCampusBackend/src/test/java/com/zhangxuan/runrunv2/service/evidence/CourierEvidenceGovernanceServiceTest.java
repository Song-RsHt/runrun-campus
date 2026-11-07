package com.zhangxuan.runrunv2.service.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhangxuan.runrunv2.integration.idempotency.OrderIdempotency;
import com.zhangxuan.runrunv2.integration.storage.V2StudentCardStorage;
import com.zhangxuan.runrunv2.mapper.evidence.CourierEvidenceGovernanceMapper;
import com.zhangxuan.runrunv2.service.identity.IdentityService;
import com.zhangxuan.runrunv2.service.impl.evidence.CourierEvidenceGovernanceServiceImpl;
import com.zhangxuan.runrunv2.vo.evidence.EvidenceHoldResultVO;
import com.zhangxuan.runrunv2.vo.identity.IdentityAccountVO;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;

class CourierEvidenceGovernanceServiceTest {

    @Test
    void holdReplaysTheStoredResultBeforeCreatingAnotherRow() {
        var mapper = mock(CourierEvidenceGovernanceMapper.class);
        IdentityService identity = mock(IdentityService.class);
        OrderIdempotency idem = mock(OrderIdempotency.class);
        when(identity.authorizeForWrite("t", "ADMIN", false))
                .thenReturn(new IdentityAccountVO("9", "ADMIN", "ACTIVE", true, 0, false));
        when(mapper.countOrderById(4L)).thenReturn(1L);
        var expected = new EvidenceHoldResultVO(12L);
        when(idem.replay(
                        eq("9"),
                        eq("evidence-hold:create"),
                        eq("k"),
                        any(),
                        eq(EvidenceHoldResultVO.class)))
                .thenReturn(expected);
        var service =
                new CourierEvidenceGovernanceServiceImpl(
                        mapper, mock(V2StudentCardStorage.class), identity, idem);
        assertEquals(
                expected,
                service.hold("t", 4L, null, "legal", Instant.parse("2030-01-01T00:00:00Z"), "k"));
        verify(mapper, never())
                .insertEvidenceHold(
                        any(), any(), any(), org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    void releaseReplaysTheStoredResultBeforeChangingTheHold() {
        var mapper = mock(CourierEvidenceGovernanceMapper.class);
        IdentityService identity = mock(IdentityService.class);
        OrderIdempotency idem = mock(OrderIdempotency.class);
        when(identity.authorizeForWrite("t", "ADMIN", false))
                .thenReturn(new IdentityAccountVO("9", "ADMIN", "ACTIVE", true, 0, false));
        var hold = new HashMap<String, Object>();
        hold.put("order_id", 4L);
        hold.put("certification_id", null);
        hold.put("released_at", null);
        when(mapper.lockEvidenceHold(7L)).thenReturn(List.of(hold));
        when(mapper.lockOrder(4L)).thenReturn(1L);
        var expected = new EvidenceHoldResultVO(7L);
        when(idem.replay(
                        eq("9"),
                        eq("evidence-hold:release"),
                        eq("k"),
                        eq("7"),
                        eq(EvidenceHoldResultVO.class)))
                .thenReturn(expected);
        var service =
                new CourierEvidenceGovernanceServiceImpl(
                        mapper, mock(V2StudentCardStorage.class), identity, idem);
        assertEquals(expected, service.release("t", 7L, "k"));
        verify(mapper, never()).releaseEvidenceHold(7L);
    }
}
