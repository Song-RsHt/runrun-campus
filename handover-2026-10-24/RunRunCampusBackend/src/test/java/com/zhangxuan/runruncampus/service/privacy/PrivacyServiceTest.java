package com.zhangxuan.runruncampus.service.privacy;

import com.zhangxuan.runruncampus.constant.PrivacyConstants;
import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.repository.SessionRepository;
import com.zhangxuan.runruncampus.repository.UserRepository;
import com.zhangxuan.runruncampus.repository.address.AddressRepository;
import com.zhangxuan.runruncampus.repository.message.OrderMessageRepository;
import com.zhangxuan.runruncampus.repository.message.PushDeliveryRepository;
import com.zhangxuan.runruncampus.repository.order.OrderRepository;
import com.zhangxuan.runruncampus.repository.payment.WechatRefundRepository;
import com.zhangxuan.runruncampus.service.PrivacyService;
import com.zhangxuan.runruncampus.service.courier.CourierCertificationService;
import com.zhangxuan.runruncampus.service.impl.PrivacyServiceImpl;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    ResponseStatusException error =
        assertThrows(ResponseStatusException.class, () -> service.consent(user, "old-version"));
    assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
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
    ResponseStatusException error =
        assertThrows(
            ResponseStatusException.class, () -> service.deleteAccount(user, "password", true));
    assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
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
    ResponseStatusException error =
        assertThrows(
            ResponseStatusException.class, () -> service.deleteAccount(user, "password", true));
    assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
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
