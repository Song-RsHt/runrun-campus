package com.zhangxuan.runrunv2.service.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.zhangxuan.runrunv2.dto.response.evidence.HoldResult;
import com.zhangxuan.runrunv2.dto.response.identity.Account;
import com.zhangxuan.runrunv2.integration.storage.V2StudentCardStorage;
import com.zhangxuan.runrunv2.mapper.evidence.CourierEvidenceGovernanceMapper;
import com.zhangxuan.runrunv2.service.idempotency.OrderIdempotency;
import com.zhangxuan.runrunv2.service.identity.IdentityService;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CourierEvidenceGovernanceServiceTest {

  @Test
  void holdReplaysTheStoredResultBeforeCreatingAnotherRow() {
    JdbcTemplate db = mock(JdbcTemplate.class);
    IdentityService identity = mock(IdentityService.class);
    OrderIdempotency idem = mock(OrderIdempotency.class);
    when(identity.authorizeForWrite("t", "ADMIN", false))
        .thenReturn(new Account("9", "ADMIN", "ACTIVE", true, 0, false));
    when(db.queryForObject(contains("v2_orders"), eq(Long.class), eq(4L))).thenReturn(1L);
    var expected = new HoldResult(12L);
    when(idem.replay(eq("9"), eq("evidence-hold:create"), eq("k"), any(), eq(HoldResult.class)))
        .thenReturn(expected);
    var service =
        new CourierEvidenceGovernanceService(
            new CourierEvidenceGovernanceMapper(db),
            mock(V2StudentCardStorage.class),
            identity,
            idem);
    assertEquals(
        expected, service.hold("t", 4L, null, "legal", Instant.parse("2030-01-01T00:00:00Z"), "k"));
    verify(db, never()).update(contains("INSERT INTO runrun_v2.evidence_holds"), (Object[]) any());
  }

  @Test
  void releaseReplaysTheStoredResultBeforeChangingTheHold() {
    JdbcTemplate db = mock(JdbcTemplate.class);
    IdentityService identity = mock(IdentityService.class);
    OrderIdempotency idem = mock(OrderIdempotency.class);
    when(identity.authorizeForWrite("t", "ADMIN", false))
        .thenReturn(new Account("9", "ADMIN", "ACTIVE", true, 0, false));
    var hold = new HashMap<String, Object>();
    hold.put("order_id", 4L);
    hold.put("certification_id", null);
    hold.put("released_at", null);
    when(db.queryForList(contains("evidence_holds"), eq(7L))).thenReturn(List.of(hold));
    when(db.queryForObject(contains("v2_orders"), eq(Long.class), eq(4L))).thenReturn(1L);
    var expected = new HoldResult(7L);
    when(idem.replay(eq("9"), eq("evidence-hold:release"), eq("k"), eq("7"), eq(HoldResult.class)))
        .thenReturn(expected);
    var service =
        new CourierEvidenceGovernanceService(
            new CourierEvidenceGovernanceMapper(db),
            mock(V2StudentCardStorage.class),
            identity,
            idem);
    assertEquals(expected, service.release("t", 7L, "k"));
    verify(db, never()).update(contains("SET released_at"), (Object[]) any());
  }
}
