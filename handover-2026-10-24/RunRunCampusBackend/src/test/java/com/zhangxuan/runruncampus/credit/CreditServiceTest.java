package com.zhangxuan.runruncampus.credit;

import com.zhangxuan.runruncampus.admin.AdminAuditService;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRepository;
import com.zhangxuan.runruncampus.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditServiceTest {
    @Mock CreditProfileRepository profiles;
    @Mock CreditEventRepository events;
    @Mock RiskSignalRepository signals;
    @Mock UserRepository users;
    @Mock AdminAuditService audit;
    private CreditService service;
    private AppUser administrator;
    private AppUser customer;

    @BeforeEach
    void setUp() {
        service = new CreditService(profiles, events, signals, users, audit);
        administrator = user(1L, "管理员", UserRole.ADMIN);
        customer = user(2L, "用户", UserRole.CUSTOMER);
    }

    @Test
    void newUserStartsAtOneHundredWithNormalRisk() {
        when(profiles.findByUserId(2L)).thenReturn(Optional.empty());
        when(profiles.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(events.findByUserIdOrderByCreatedAtDesc(eq(2L), any())).thenReturn(Page.empty());
        when(signals.countBySubjectIdAndStatus(2L, RiskSignalStatus.PENDING)).thenReturn(0L);

        var result = service.profile(customer, 0, 20);

        assertEquals(100, result.score());
        assertEquals(CreditRiskLevel.NORMAL, result.riskLevel());
        assertEquals(0, result.events().size());
    }

    @Test
    void administratorAdjustmentChangesScoreAndWritesAnAuditedEvent() {
        CreditProfile profile = new CreditProfile(customer);
        when(users.findLockedById(2L)).thenReturn(Optional.of(customer));
        when(profiles.findLockedByUserId(2L)).thenReturn(Optional.of(profile));
        when(events.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.adjust(administrator, 2L, -25, "核实多次恶意取消");

        assertEquals(75, result.score());
        assertEquals(CreditRiskLevel.WATCH, result.riskLevel());
        ArgumentCaptor<CreditEvent> saved = ArgumentCaptor.forClass(CreditEvent.class);
        verify(events).save(saved.capture());
        assertEquals(-25, saved.getValue().getDelta());
        assertEquals(CreditEventType.ADMIN_ADJUSTMENT, saved.getValue().getType());
        verify(audit).record(eq(administrator), any(), any(), eq(2L), any());
    }

    @Test
    void adjustmentCanBeReversedOnlyOnce() {
        CreditProfile profile = new CreditProfile(customer);
        profile.apply(-20);
        CreditEvent original = new CreditEvent(customer, administrator, CreditEventType.ADMIN_ADJUSTMENT,
                -20, 80, "核实异常", null, null);
        ReflectionTestUtils.setField(original, "id", 9L);
        when(users.findLockedById(2L)).thenReturn(Optional.of(customer));
        when(profiles.findLockedByUserId(2L)).thenReturn(Optional.of(profile));
        when(events.findByIdAndUserId(9L, 2L)).thenReturn(Optional.of(original));
        when(events.existsByOriginalEventId(9L)).thenReturn(false, true);
        when(events.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var reversed = service.reverse(administrator, 2L, 9L, "复核后撤销");

        assertEquals(100, reversed.score());
        assertEquals(CreditEventType.REVERSAL, reversed.type());
        assertThrows(ResponseStatusException.class,
                () -> service.reverse(administrator, 2L, 9L, "重复撤销"));
    }

    @Test
    void dismissingSignalNeverChangesCredit() {
        RiskSignal signal = new RiskSignal(customer, RiskSignalType.FREQUENT_CANCELLATION,
                "CANCEL:2:3", null, "24小时内取消3单");
        ReflectionTestUtils.setField(signal, "id", 7L);
        when(signals.findLockedById(7L)).thenReturn(Optional.of(signal));

        service.dismissSignal(administrator, 7L, "核实为正常取消");

        assertEquals(RiskSignalStatus.DISMISSED, signal.getStatus());
        verify(profiles, never()).findLockedByUserId(anyLong());
        verify(events, never()).save(any());
    }

    private AppUser user(long id, String name, UserRole role) {
        AppUser user = new AppUser("1380000000" + id, "hash", name, role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
