package com.zhangxuan.runruncampus.privacy;

import com.zhangxuan.runruncampus.address.AddressRepository;
import com.zhangxuan.runruncampus.auth.SessionRepository;
import com.zhangxuan.runruncampus.courier.CourierCertificationService;
import com.zhangxuan.runruncampus.message.OrderMessageRepository;
import com.zhangxuan.runruncampus.message.PushDeliveryRepository;
import com.zhangxuan.runruncampus.order.OrderRepository;
import com.zhangxuan.runruncampus.payment.WechatRefundRepository;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRepository;
import com.zhangxuan.runruncampus.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrivacyServiceTest {
    @Mock UserRepository users;
    @Mock OrderRepository orders;
    @Mock WechatRefundRepository refunds;
    @Mock SessionRepository sessions;
    @Mock AddressRepository addresses;
    @Mock CourierCertificationService certifications;
    @Mock OrderMessageRepository messages;
    @Mock PushDeliveryRepository deliveries;
    @Mock PasswordEncoder passwords;
    private PrivacyService service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        service = new PrivacyService(users, orders, refunds, sessions, addresses,
                certifications, messages, deliveries, passwords);
        user = new AppUser("13800138000", "old-hash", "测试用户", UserRole.CUSTOMER);
        ReflectionTestUtils.setField(user, "id", 42L);
    }

    @Test
    void policyContainsConfirmedOperatorAndContact() {
        var policy = service.policy();

        assertEquals("射阳县合德镇轩轩生活服务部", policy.operatorName());
        assertEquals("15151050678", policy.contact());
        assertNotNull(policy.version());
        assertFalse(policy.content().isBlank());
    }

    @Test
    void consentOnlyAcceptsTheCurrentPublishedVersion() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.consent(user, "old-version"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        service.consent(user, PrivacyService.CURRENT_VERSION);
        assertEquals(PrivacyService.CURRENT_VERSION, user.getPrivacyPolicyVersion());
        assertNotNull(user.getPrivacyConsentAt());
    }

    @Test
    void withdrawalClearsConsentAndRevokesSessionsWithoutDeletingAccount() {
        user.recordPrivacyConsent(PrivacyService.CURRENT_VERSION);
        when(users.findLockedById(42L)).thenReturn(Optional.of(user));

        service.withdrawConsent(user);

        assertNull(user.getPrivacyPolicyVersion());
        assertNull(user.getPrivacyConsentAt());
        verify(sessions).deleteByUser(user);
        verify(addresses, never()).deleteByUser(user);
    }

    @Test
    void activeOrdersPreventAccountDeletion() {
        when(users.findLockedById(42L)).thenReturn(Optional.of(user));
        when(passwords.matches("password", "old-hash")).thenReturn(true);
        when(orders.countByCustomerAndStatusIn(user, PrivacyService.ACTIVE_ORDER_STATUSES)).thenReturn(1L);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.deleteAccount(user, "password", true));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(sessions, never()).deleteByUser(user);
    }

    @Test
    void unfinishedRefundPreventsAccountDeletion() {
        when(users.findLockedById(42L)).thenReturn(Optional.of(user));
        when(passwords.matches("password", "old-hash")).thenReturn(true);
        when(orders.countByCustomerAndStatusIn(user, PrivacyService.ACTIVE_ORDER_STATUSES)).thenReturn(0L);
        when(orders.countByCourierAndStatusIn(user, PrivacyService.ACTIVE_ORDER_STATUSES)).thenReturn(0L);
        when(refunds.existsUnfinishedByUser(user)).thenReturn(true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.deleteAccount(user, "password", true));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(sessions, never()).deleteByUser(user);
    }

    @Test
    void deletionRemovesDirectPersonalDataAndAnonymizesAccount() {
        when(users.findLockedById(42L)).thenReturn(Optional.of(user));
        when(passwords.matches("password", "old-hash")).thenReturn(true);
        when(orders.countByCustomerAndStatusIn(user, PrivacyService.ACTIVE_ORDER_STATUSES)).thenReturn(0L);
        when(orders.countByCourierAndStatusIn(user, PrivacyService.ACTIVE_ORDER_STATUSES)).thenReturn(0L);
        when(refunds.existsUnfinishedByUser(user)).thenReturn(false);
        when(passwords.encode(org.mockito.ArgumentMatchers.anyString())).thenReturn("deleted-hash");

        service.deleteAccount(user, "password", true);

        assertEquals("deleted-42", user.getPhone());
        assertEquals("已注销用户", user.getDisplayName());
        assertFalse(user.isEnabled());
        assertNotNull(user.getDeletedAt());
        verify(certifications).deletePersonalData(user);
        verify(addresses).deleteByUser(user);
        verify(messages).deleteByRecipient(user);
        verify(deliveries).deleteByRecipient(42L);
        verify(sessions).deleteByUser(user);
    }
}
