package com.zhangxuan.runruncampus.service.auth;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.Session;
import com.zhangxuan.runruncampus.enums.AdminPermission;
import com.zhangxuan.runruncampus.enums.AdminRole;
import com.zhangxuan.runruncampus.enums.SessionClientType;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.repository.SessionRepository;
import com.zhangxuan.runruncampus.repository.UserRepository;
import com.zhangxuan.runruncampus.service.AuthService;
import com.zhangxuan.runruncampus.service.LoginAttemptService;
import com.zhangxuan.runruncampus.service.LoginCaptchaService;
import com.zhangxuan.runruncampus.service.impl.AuthServiceImpl;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import com.zhangxuan.runruncampus.exception.BusinessFailure;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;


































































@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private UserRepository userRepository;

  @Mock private SessionRepository sessionRepository;

  @Mock private PasswordEncoder passwordEncoder;

  @Mock private LoginAttemptService loginAttemptService;

  @Mock private LoginCaptchaService captchaService;

  private AuthService authService;

  @BeforeEach
  void setUp() {
    authService =
        new AuthServiceImpl(
            userRepository,
            sessionRepository,
            passwordEncoder,
            loginAttemptService,
            captchaService,
            24);
  }

  @Test
  void publicRegistrationCannotCreateAdmin() {
    BusinessFailure exception =
        assertThrows(
            BusinessFailure.class,
            () -> authService.register("13900000000", "password", "管理员", UserRole.ADMIN));
    assertEquals(HttpStatus.FORBIDDEN.value(), exception.status());
    verifyNoInteractions(userRepository, sessionRepository, passwordEncoder);
  }

  @Test
  void newCourierRegistrationStartsUnapproved() {
    when(userRepository.findByPhone("13800000001")).thenReturn(Optional.empty());
    when(passwordEncoder.encode("password")).thenReturn("password-hash");
    when(userRepository.save(any(AppUser.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(sessionRepository.save(any(Session.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    Session session = authService.register("13800000001", "password", "新骑手", UserRole.COURIER);
    assertEquals(UserRole.COURIER, session.getUser().getRole());
    assertFalse(session.getUser().isCourierApproved());
  }

  @Test
  void customerCannotUseAdminSession() {
    AppUser customer = new AppUser("13800000002", "hash", "普通用户", UserRole.CUSTOMER);
    when(sessionRepository.findByToken("customer-token"))
        .thenReturn(
            Optional.of(new Session("customer-token", customer, Instant.now().plusSeconds(3600))));
    BusinessFailure exception =
        assertThrows(
            BusinessFailure.class, () -> authService.requireAdmin("Bearer customer-token"));
    assertEquals(HttpStatus.FORBIDDEN.value(), exception.status());
  }

  @Test
  void administratorCanUseAdminSession() {
    AppUser admin = new AppUser("13800000003", "hash", "平台管理员", UserRole.ADMIN);
    Session verified = new Session("admin-token", admin, Instant.now().plusSeconds(3600));
    verified.markAdminMfaVerified();
    when(sessionRepository.findByToken("admin-token")).thenReturn(Optional.of(verified));
    assertEquals(admin, authService.requireAdmin("Bearer admin-token"));
  }

  @Test
  void supportAdministratorCannotReadAuditLogs() {
    AppUser admin = new AppUser("13800000004", "hash", "客服管理员", UserRole.ADMIN);
    ReflectionTestUtils.setField(admin, "adminRole", AdminRole.SUPPORT);
    Session verified = new Session("support-token", admin, Instant.now().plusSeconds(3600));
    verified.markAdminMfaVerified();
    when(sessionRepository.findByToken("support-token")).thenReturn(Optional.of(verified));
    assertEquals(
        admin, authService.requirePermission("Bearer support-token", AdminPermission.ISSUE_MANAGE));
    BusinessFailure exception =
        assertThrows(
            BusinessFailure.class,
            () ->
                authService.requirePermission("Bearer support-token", AdminPermission.AUDIT_VIEW));
    assertEquals(HttpStatus.FORBIDDEN.value(), exception.status());
    assertEquals("当前管理员岗位无此权限", exception.getReason());
  }

  @Test
  void wrongPasswordRecordsFailureWithoutRevealingAccountDetails() {
    AppUser user = new AppUser("13800000005", "hash", "测试用户", UserRole.CUSTOMER);
    ReflectionTestUtils.setField(user, "id", 5L);
    when(userRepository.findByPhone("13800000005")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);
    BusinessFailure exception =
        assertThrows(
            BusinessFailure.class, () -> authService.login("13800000005", "wrong"));
    assertEquals(HttpStatus.UNAUTHORIZED.value(), exception.status());
    assertEquals("手机号或密码错误", exception.getReason());
    verify(loginAttemptService).recordFailure(5L);
  }

  @Test
  void lockedAccountIsRejectedBeforePasswordVerification() {
    AppUser user = new AppUser("13800000006", "hash", "锁定用户", UserRole.CUSTOMER);
    ReflectionTestUtils.setField(user, "loginLockedUntil", Instant.now().plusSeconds(300));
    when(userRepository.findByPhone("13800000006")).thenReturn(Optional.of(user));
    BusinessFailure exception =
        assertThrows(
            BusinessFailure.class, () -> authService.login("13800000006", "password"));
    assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), exception.status());
    verifyNoInteractions(passwordEncoder);
    verify(loginAttemptService).requiresCaptcha("13800000006");
  }

  @Test
  void riskyLoginRequiresCaptchaBeforeCheckingPassword() {
    when(loginAttemptService.requiresCaptcha("13800000007")).thenReturn(true);
    Mockito.doThrow(
            new BusinessFailure(HttpStatus.PRECONDITION_REQUIRED.value(), "请输入正确的图形验证码"))
        .when(captchaService)
        .verify(null, null);
    BusinessFailure exception =
        assertThrows(
            BusinessFailure.class, () -> authService.login("13800000007", "password"));
    assertEquals(HttpStatus.PRECONDITION_REQUIRED.value(), exception.status());
    verifyNoInteractions(userRepository, passwordEncoder, sessionRepository);
  }

  @Test
  void sessionListMarksCurrentTokenWithoutExposingIt() {
    AppUser customer = new AppUser("13800000008", "hash", "普通用户", UserRole.CUSTOMER);
    Session current =
        new Session(
            "current-token",
            customer,
            Instant.now().plusSeconds(3600),
            SessionClientType.ANDROID,
            "Pixel 9");
    Session other =
        new Session(
            "other-token",
            customer,
            Instant.now().plusSeconds(3600),
            SessionClientType.ANDROID,
            "Xiaomi 15");
    ReflectionTestUtils.setField(current, "id", 80L);
    ReflectionTestUtils.setField(other, "id", 81L);
    when(sessionRepository.findByToken("current-token")).thenReturn(Optional.of(current));
    when(sessionRepository.findByUserAndExpiresAtAfterOrderByCreatedAtDesc(
            any(AppUser.class), any(Instant.class)))
        .thenReturn(List.of(current, other));
    var sessions = authService.sessions("Bearer current-token");
    assertEquals(2, sessions.size());
    assertEquals("Pixel 9", sessions.get(0).deviceName());
    assertEquals(true, sessions.get(0).current());
    assertEquals(false, sessions.get(1).current());
  }

  @Test
  void currentSessionCannotBeRevokedThroughDeviceEndpoint() {
    AppUser customer = new AppUser("13800000009", "hash", "普通用户", UserRole.CUSTOMER);
    Session current = new Session("current-token", customer, Instant.now().plusSeconds(3600));
    ReflectionTestUtils.setField(current, "id", 90L);
    when(sessionRepository.findByToken("current-token")).thenReturn(Optional.of(current));
    BusinessFailure exception =
        assertThrows(
            BusinessFailure.class,
            () -> authService.revokeSession("Bearer current-token", 90L));
    assertEquals(HttpStatus.CONFLICT.value(), exception.status());
  }

  @Test
  void wrongCurrentPasswordDoesNotChangePasswordOrSessions() {
    AppUser customer = new AppUser("13800000010", "old-hash", "普通用户", UserRole.CUSTOMER);
    ReflectionTestUtils.setField(customer, "id", 10L);
    Session current = new Session("current-token", customer, Instant.now().plusSeconds(3600));
    when(sessionRepository.findByToken("current-token")).thenReturn(Optional.of(current));
    when(userRepository.findLockedById(10L)).thenReturn(Optional.of(customer));
    when(passwordEncoder.matches("wrong", "old-hash")).thenReturn(false);
    BusinessFailure exception =
        assertThrows(
            BusinessFailure.class,
            () -> authService.changeOwnPassword("Bearer current-token", "wrong", "new-password"));
    assertEquals(HttpStatus.BAD_REQUEST.value(), exception.status());
    assertEquals("old-hash", customer.getPasswordHash());
    verify(sessionRepository, never()).deleteByUser(customer);
  }

  @Test
  void samePasswordIsRejectedWithoutRevokingSessions() {
    AppUser courier = new AppUser("13800000011", "old-hash", "骑手", UserRole.COURIER);
    ReflectionTestUtils.setField(courier, "id", 11L);
    Session current = new Session("current-token", courier, Instant.now().plusSeconds(3600));
    when(sessionRepository.findByToken("current-token")).thenReturn(Optional.of(current));
    when(userRepository.findLockedById(11L)).thenReturn(Optional.of(courier));
    when(passwordEncoder.matches("current-password", "old-hash")).thenReturn(true);
    BusinessFailure exception =
        assertThrows(
            BusinessFailure.class,
            () ->
                authService.changeOwnPassword(
                    "Bearer current-token", "current-password", "current-password"));
    assertEquals(HttpStatus.BAD_REQUEST.value(), exception.status());
    verify(sessionRepository, never()).deleteByUser(courier);
  }

  @Test
  void successfulPasswordChangeRevokesAllSessions() {
    AppUser customer = new AppUser("13800000012", "old-hash", "普通用户", UserRole.CUSTOMER);
    ReflectionTestUtils.setField(customer, "id", 12L);
    Session current = new Session("current-token", customer, Instant.now().plusSeconds(3600));
    when(sessionRepository.findByToken("current-token")).thenReturn(Optional.of(current));
    when(userRepository.findLockedById(12L)).thenReturn(Optional.of(customer));
    when(passwordEncoder.matches("current-password", "old-hash")).thenReturn(true);
    when(passwordEncoder.matches("new-password", "old-hash")).thenReturn(false);
    when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
    authService.changeOwnPassword("Bearer current-token", "current-password", "new-password");
    assertEquals("new-hash", customer.getPasswordHash());
    verify(sessionRepository).deleteByUser(customer);
  }
}
