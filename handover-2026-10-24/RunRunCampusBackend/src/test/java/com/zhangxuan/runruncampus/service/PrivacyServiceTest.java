package com.zhangxuan.runruncampus.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhangxuan.runruncampus.constant.PrivacyConstants;
import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.exception.BusinessFailure;
import com.zhangxuan.runruncampus.repository.AddressRepository;
import com.zhangxuan.runruncampus.repository.OrderMessageRepository;
import com.zhangxuan.runruncampus.repository.OrderRepository;
import com.zhangxuan.runruncampus.repository.PushDeliveryRepository;
import com.zhangxuan.runruncampus.repository.SessionRepository;
import com.zhangxuan.runruncampus.repository.UserRepository;
import com.zhangxuan.runruncampus.repository.WechatRefundRepository;
import com.zhangxuan.runruncampus.service.impl.PrivacyServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

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
        service =
                new PrivacyServiceImpl(
                        users,
                        orders,
                        refunds,
                        sessions,
                        addresses,
                        certifications,
                        messages,
                        deliveries,
                        passwords);
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
        BusinessFailure error =
                assertThrows(BusinessFailure.class, () -> service.consent(user, "old-version"));
        assertEquals(HttpStatus.CONFLICT.value(), error.status());
        service.consent(user, PrivacyConstants.CURRENT_VERSION);
        assertEquals(PrivacyConstants.CURRENT_VERSION, user.getPrivacyPolicyVersion());
        assertNotNull(user.getPrivacyConsentAt());
    }

    @Test
    void withdrawalClearsConsentAndRevokesSessionsWithoutDeletingAccount() {
        user.recordPrivacyConsent(PrivacyConstants.CURRENT_VERSION);
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
        when(orders.countByCustomerAndStatusIn(user, PrivacyConstants.ACTIVE_ORDER_STATUSES))
                .thenReturn(1L);
        BusinessFailure error =
                assertThrows(
                        BusinessFailure.class, () -> service.deleteAccount(user, "password", true));
        assertEquals(HttpStatus.CONFLICT.value(), error.status());
        verify(sessions, never()).deleteByUser(user);
    }

    @Test
    void unfinishedRefundPreventsAccountDeletion() {
        when(users.findLockedById(42L)).thenReturn(Optional.of(user));
        when(passwords.matches("password", "old-hash")).thenReturn(true);
        when(orders.countByCustomerAndStatusIn(user, PrivacyConstants.ACTIVE_ORDER_STATUSES))
                .thenReturn(0L);
        when(orders.countByCourierAndStatusIn(user, PrivacyConstants.ACTIVE_ORDER_STATUSES))
                .thenReturn(0L);
        when(refunds.existsUnfinishedByUser(user)).thenReturn(true);
        BusinessFailure error =
                assertThrows(
                        BusinessFailure.class, () -> service.deleteAccount(user, "password", true));
        assertEquals(HttpStatus.CONFLICT.value(), error.status());
        verify(sessions, never()).deleteByUser(user);
    }

    @Test
    void deletionRemovesDirectPersonalDataAndAnonymizesAccount() {
        when(users.findLockedById(42L)).thenReturn(Optional.of(user));
        when(passwords.matches("password", "old-hash")).thenReturn(true);
        when(orders.countByCustomerAndStatusIn(user, PrivacyConstants.ACTIVE_ORDER_STATUSES))
                .thenReturn(0L);
        when(orders.countByCourierAndStatusIn(user, PrivacyConstants.ACTIVE_ORDER_STATUSES))
                .thenReturn(0L);
        when(refunds.existsUnfinishedByUser(user)).thenReturn(false);
        when(passwords.encode(ArgumentMatchers.anyString())).thenReturn("deleted-hash");
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
