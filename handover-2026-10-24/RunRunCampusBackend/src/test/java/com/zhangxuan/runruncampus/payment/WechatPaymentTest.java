package com.zhangxuan.runruncampus.payment;

import com.zhangxuan.runruncampus.membership.MembershipPlanType;
import com.zhangxuan.runruncampus.order.CampusOrder;
import com.zhangxuan.runruncampus.order.ServiceType;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class WechatPaymentTest {
    private final AppUser customer = user(1L);

    @Test
    void paidTransitionIsIdempotentButClosedPaymentCannotBecomePaid() {
        WechatPayment payment = WechatPayment.forMembership(customer, MembershipPlanType.LIGHT,
                Instant.now().plusSeconds(1800));
        payment.markPaid("wx-transaction-1");
        payment.markPaid("wx-transaction-1");
        assertEquals(PaymentStatus.PAID, payment.getStatus());

        WechatPayment closed = WechatPayment.forMembership(customer, MembershipPlanType.LIGHT,
                Instant.now().plusSeconds(1800));
        closed.close();
        assertThrows(IllegalStateException.class, () -> closed.markPaid("late"));
    }

    @Test
    void paymentBindsAmountAndBusinessOnServer() {
        CampusOrder order = new CampusOrder("RP-1", customer, "商家", "1234", "东门", "3号楼", ServiceType.DOOR);
        WechatPayment orderPayment = WechatPayment.forOrder(customer, order, 300, Instant.now().plusSeconds(1800));
        WechatPayment membershipPayment = WechatPayment.forMembership(customer, MembershipPlanType.STUDENT,
                Instant.now().plusSeconds(1800));

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
