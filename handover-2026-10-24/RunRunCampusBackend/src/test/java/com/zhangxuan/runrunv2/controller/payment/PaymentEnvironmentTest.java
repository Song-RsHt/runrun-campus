package com.zhangxuan.runrunv2.controller.payment;

import com.zhangxuan.runrunv2.controller.PaymentTestController;
import com.zhangxuan.runrunv2.integration.payment.MockPaymentAdapter;
import com.zhangxuan.runrunv2.mapper.MockPaymentMapper;
import com.zhangxuan.runrunv2.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;


























class PaymentEnvironmentTest {

  final ApplicationContextRunner routes =
      new ApplicationContextRunner()
          .withBean(PaymentService.class, () -> mock(PaymentService.class))
          .withUserConfiguration(PaymentTestController.class);

  @Test
  void productionNeverRegistersTestRoutesEvenWhenFlagIsOn() {
    for (String env : new String[] {"production", "prod", "unknown"})
      routes
          .withPropertyValues("rrc.identity.environment=" + env, "rrc.payment.mock-enabled=true")
          .run(
              context ->
                  assertEquals(0, context.getBeansOfType(PaymentTestController.class).size()));
    routes
        .withPropertyValues(
            "rrc.identity.environment=test",
            "rrc.payment.mock-enabled=true",
            "spring.profiles.active=production")
        .run(
            context -> assertEquals(0, context.getBeansOfType(PaymentTestController.class).size()));
  }

  @Test
  void defaultIsOffAndOnlyExplicitIsolatedFlagEnablesRoutes() {
    routes
        .withPropertyValues("rrc.identity.environment=test")
        .run(
            context -> assertEquals(0, context.getBeansOfType(PaymentTestController.class).size()));
    for (String env : new String[] {"local", "test", "staging"})
      routes
          .withPropertyValues("rrc.identity.environment=" + env, "rrc.payment.mock-enabled=true")
          .run(
              context ->
                  assertEquals(1, context.getBeansOfType(PaymentTestController.class).size()));
  }

  @Test
  void adapterCannotStartInProductionOrUnderProductionProfile() {
    assertThrows(
        IllegalStateException.class,
        () ->
            new MockPaymentAdapter(
                new MockPaymentMapper(mock(JdbcTemplate.class)),
                "production",
                new StandardEnvironment()));
    var environment = new StandardEnvironment();
    environment.setActiveProfiles("production");
    assertThrows(
        IllegalStateException.class,
        () ->
            new MockPaymentAdapter(
                new MockPaymentMapper(mock(JdbcTemplate.class)), "test", environment));
  }
}
