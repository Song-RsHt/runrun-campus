package com.zhangxuan.runruncampus.model.payment;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.enums.membership.MembershipPlanType;
import com.zhangxuan.runruncampus.enums.order.ServiceType;
import com.zhangxuan.runruncampus.enums.payment.PaymentBusinessType;
import com.zhangxuan.runruncampus.enums.payment.PaymentStatus;
import com.zhangxuan.runruncampus.model.order.CampusOrder;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;















class WechatPaymentTest {

  private final AppUser customer = user(1L);

  @Test
  void paidTransitionIsIdempotentButClosedPaymentCannotBecomePaid() {
    WechatPayment payment =
        WechatPayment.forMembership(
            customer, MembershipPlanType.LIGHT, Instant.now().plusSeconds(1800));
    payment.markPaid("wx-transaction-1");
    payment.markPaid("wx-transaction-1");
    assertEquals(PaymentStatus.PAID, payment.getStatus());
    WechatPayment closed =
        WechatPayment.forMembership(
            customer, MembershipPlanType.LIGHT, Instant.now().plusSeconds(1800));
    closed.close();
    assertThrows(IllegalStateException.class, () -> closed.markPaid("late"));
  }

  @Test
  void paymentBindsAmountAndBusinessOnServer() {
    CampusOrder order =
        new CampusOrder("RP-1", customer, "商家", "1234", "东门", "3号楼", ServiceType.DOOR);
    WechatPayment orderPayment =
        WechatPayment.forOrder(customer, order, 300, Instant.now().plusSeconds(1800));
    WechatPayment membershipPayment =
        WechatPayment.forMembership(
            customer, MembershipPlanType.STUDENT, Instant.now().plusSeconds(1800));
    assertEquals(PaymentBusinessType.ORDER, orderPayment.getBusinessType());
    assertEquals(300, orderPayment.getAmountCents());
    assertSame(order, orderPayment.getOrder());
    assertEquals(PaymentBusinessType.MEMBERSHIP, membershipPayment.getBusinessType());
    assertEquals(2900, membershipPayment.getAmountCents());
  }

  private AppUser user(Long id) {
    AppUser user = new AppUser("13800000001", "hash", "用户", UserRole.CUSTOMER);
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }
}
