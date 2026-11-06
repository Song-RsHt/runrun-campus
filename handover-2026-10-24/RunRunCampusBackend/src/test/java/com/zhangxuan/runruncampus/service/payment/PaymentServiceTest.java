package com.zhangxuan.runruncampus.service.payment;

import com.zhangxuan.runruncampus.config.WechatPayProperties;
import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.CampusOrder;
import com.zhangxuan.runruncampus.entity.WechatPayment;
import com.zhangxuan.runruncampus.entity.WechatRefund;
import com.zhangxuan.runruncampus.enums.MembershipPlanType;
import com.zhangxuan.runruncampus.enums.OrderPaymentStatus;
import com.zhangxuan.runruncampus.enums.OrderStatus;
import com.zhangxuan.runruncampus.enums.PaymentBusinessType;
import com.zhangxuan.runruncampus.enums.PaymentStatus;
import com.zhangxuan.runruncampus.enums.RefundStatus;
import com.zhangxuan.runruncampus.enums.ServiceType;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.integration.payment.WechatPayGateway;
import com.zhangxuan.runruncampus.model.AppPayParameters;
import com.zhangxuan.runruncampus.model.NotificationHeaders;
import com.zhangxuan.runruncampus.model.RefundNotificationResult;
import com.zhangxuan.runruncampus.model.TradeResult;
import com.zhangxuan.runruncampus.repository.OrderRepository;
import com.zhangxuan.runruncampus.repository.WechatPaymentRepository;
import com.zhangxuan.runruncampus.repository.WechatRefundRepository;
import com.zhangxuan.runruncampus.service.MembershipService;
import com.zhangxuan.runruncampus.service.PaymentService;
import com.zhangxuan.runruncampus.service.impl.PaymentServiceImpl;
import com.zhangxuan.runruncampus.vo.PaymentResponse;
import com.zhangxuan.runruncampus.vo.RefundResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;


























































































@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

  @Mock WechatPaymentRepository paymentRepository;

  @Mock WechatRefundRepository refundRepository;

  @Mock OrderRepository orderRepository;

  @Mock MembershipService membershipService;

  @Mock WechatPayGateway gateway;

  private PaymentService service;

  private AppUser customer;

  private CampusOrder order;

  @BeforeEach
  void setUp() {
    service =
        new PaymentServiceImpl(
            paymentRepository,
            refundRepository,
            orderRepository,
            membershipService,
            gateway,
            new WechatPayProperties(
                true,
                "wx-app",
                "merchant",
                "serial",
                "key.pem",
                "12345678901234567890123456789012",
                null,
                "https://api.example.test/notify",
                "https://api.example.test/refund"));
    customer = user(1L);
    order = new CampusOrder("RP-100", customer, "商家", "1234", "东门", "3号楼", ServiceType.DOOR);
    ReflectionTestUtils.setField(order, "id", 10L);
    order.requirePayment();
  }

  @Test
  void orderPrepayAmountComesFromLockedOrder() {
    when(orderRepository.findLockedById(10L)).thenReturn(Optional.of(order));
    when(paymentRepository.findFirstByOrderAndStatusOrderByCreatedAtDesc(
            order, PaymentStatus.PENDING))
        .thenReturn(Optional.empty());
    when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(gateway.prepay(anyString(), anyString(), eq(300), eq("ORDER:10"), any(Instant.class)))
        .thenReturn(parameters());
    PaymentResponse response = service.createOrderPayment(customer, 10L);
    assertEquals(300, response.amountCents());
    assertEquals(PaymentStatus.PENDING, response.status());
    assertEquals("prepay", response.payParameters().prepayId());
  }

  @Test
  void cashOnDeliveryOrderCannotStartOnlinePayment() {
    order.requireCashOnDelivery();
    when(orderRepository.findLockedById(10L)).thenReturn(Optional.of(order));
    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class, () -> service.createOrderPayment(customer, 10L));
    assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    assertEquals("该订单为货到付款，无需发起线上支付", exception.getReason());
    verifyNoInteractions(paymentRepository, gateway);
  }

  @Test
  void verifiedMatchingNotificationMarksOrderPaidOnlyOnce() {
    WechatPayment payment = pendingOrderPayment();
    TradeResult result =
        new TradeResult("wx-app", "merchant", payment.getOutTradeNo(), "wx-transaction", 300, true);
    when(gateway.parsePaymentNotification(any(), anyString())).thenReturn(result);
    when(paymentRepository.findLockedByOutTradeNo(payment.getOutTradeNo()))
        .thenReturn(Optional.of(payment));
    when(orderRepository.findLockedById(10L)).thenReturn(Optional.of(order));
    service.acceptPaymentNotification(headers(), "signed-body");
    service.acceptPaymentNotification(headers(), "signed-body");
    assertEquals(PaymentStatus.PAID, payment.getStatus());
    assertEquals(OrderPaymentStatus.PAID, order.getPaymentStatus());
    assertEquals(300, order.getPaidCents());
    verify(orderRepository, times(1)).findLockedById(10L);
  }

  @Test
  void mismatchedAmountCannotActivateOrder() {
    WechatPayment payment = pendingOrderPayment();
    TradeResult result =
        new TradeResult("wx-app", "merchant", payment.getOutTradeNo(), "wx-transaction", 1, true);
    when(gateway.parsePaymentNotification(any(), anyString())).thenReturn(result);
    when(paymentRepository.findLockedByOutTradeNo(payment.getOutTradeNo()))
        .thenReturn(Optional.of(payment));
    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () -> service.acceptPaymentNotification(headers(), "signed-body"));
    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    assertEquals(OrderPaymentStatus.UNPAID, order.getPaymentStatus());
    verifyNoInteractions(membershipService);
  }

  @Test
  void membershipActivatesOnlyAfterVerifiedPayment() {
    when(paymentRepository.findFirstByUserAndBusinessTypeAndStatusOrderByCreatedAtDesc(
            customer, PaymentBusinessType.MEMBERSHIP, PaymentStatus.PENDING))
        .thenReturn(Optional.empty());
    when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(gateway.prepay(anyString(), anyString(), eq(2900), anyString(), any(Instant.class)))
        .thenReturn(parameters());
    PaymentResponse response =
        service.createMembershipPayment(customer, MembershipPlanType.STUDENT);
    verify(membershipService).validateCanPurchase(customer);
    verify(membershipService, never()).activatePaidPurchase(any(), any());
    WechatPayment payment = paymentFromSave();
    TradeResult result =
        new TradeResult("wx-app", "merchant", response.outTradeNo(), "wx-membership", 2900, true);
    when(gateway.parsePaymentNotification(any(), anyString())).thenReturn(result);
    when(paymentRepository.findLockedByOutTradeNo(response.outTradeNo()))
        .thenReturn(Optional.of(payment));
    service.acceptPaymentNotification(headers(), "signed-body");
    verify(membershipService).activatePaidPurchase(customer, MembershipPlanType.STUDENT);
  }

  @Test
  void cancellingPaidOrderQueuesIdempotentRefundAmount() {
    CampusOrder paidOrder =
        new CampusOrder("RP-101", customer, "商家", "1234", "东门", "3号楼", ServiceType.DOOR);
    WechatPayment payment =
        WechatPayment.forOrder(customer, paidOrder, 300, Instant.now().plusSeconds(1800));
    payment.markPaid("wx-paid");
    when(paymentRepository.findByOrderAndStatusOrderByCreatedAtAsc(
            paidOrder, PaymentStatus.PENDING))
        .thenReturn(List.of());
    when(paymentRepository.findByOrderAndStatusOrderByCreatedAtAsc(paidOrder, PaymentStatus.PAID))
        .thenReturn(List.of(payment));
    when(refundRepository.findByPaymentOrderByCreatedAtAsc(payment)).thenReturn(List.of());
    service.closeAndRefundForCancellation(paidOrder);
    ArgumentCaptor<WechatRefund> refund = ArgumentCaptor.forClass(WechatRefund.class);
    verify(refundRepository).save(refund.capture());
    assertEquals(300, refund.getValue().getAmountCents());
    assertEquals(OrderPaymentStatus.REFUNDING, paidOrder.getPaymentStatus());
    assertEquals(0, paidOrder.getPaidCents());
  }

  @Test
  void verifiedRefundNotificationCompletesCancelledOrderRefund() {
    CampusOrder paidOrder =
        new CampusOrder("RP-102", customer, "商家", "1234", "东门", "3号楼", ServiceType.DOOR);
    paidOrder.updateStatus(OrderStatus.CANCELLED);
    paidOrder.recordRefundScheduled(300, true);
    WechatPayment payment =
        WechatPayment.forOrder(customer, paidOrder, 300, Instant.now().plusSeconds(1800));
    payment.markPaid("wx-paid-refund");
    WechatRefund refund = new WechatRefund(payment, 300, "订单取消");
    RefundNotificationResult result =
        new RefundNotificationResult(
            payment.getOutTradeNo(),
            refund.getOutRefundNo(),
            "wx-refund",
            300,
            RefundStatus.SUCCESS);
    when(gateway.parseRefundNotification(any(), anyString())).thenReturn(result);
    when(refundRepository.findLockedByOutRefundNo(refund.getOutRefundNo()))
        .thenReturn(Optional.of(refund));
    when(paymentRepository.sumPaidForOrder(paidOrder)).thenReturn(300L);
    when(refundRepository.sumSucceededForOrder(paidOrder)).thenReturn(300L);
    service.acceptRefundNotification(headers(), "signed-body");
    assertEquals(RefundStatus.SUCCESS, refund.getStatus());
    assertEquals(OrderPaymentStatus.REFUNDED, paidOrder.getPaymentStatus());
  }

  @Test
  void orderRefundHistoryContainsAmountReasonAndProgress() {
    WechatPayment payment =
        WechatPayment.forOrder(customer, order, 300, Instant.now().plusSeconds(1800));
    WechatRefund refund = new WechatRefund(payment, 200, "订单取消");
    when(refundRepository.findByOrder(order)).thenReturn(List.of(refund));
    RefundResponse response = service.refundsForOrder(order).get(0);
    assertEquals(200, response.amountCents());
    assertEquals("订单取消", response.reason());
    assertEquals(RefundStatus.PENDING, response.status());
    assertNotNull(response.createdAt());
    assertNotNull(response.updatedAt());
  }

  private WechatPayment pendingOrderPayment() {
    WechatPayment payment =
        WechatPayment.forOrder(customer, order, 300, Instant.now().plusSeconds(1800));
    payment.recordPrepay(parameters());
    return payment;
  }

  private WechatPayment paymentFromSave() {
    ArgumentCaptor<WechatPayment> captor = ArgumentCaptor.forClass(WechatPayment.class);
    verify(paymentRepository).save(captor.capture());
    return captor.getValue();
  }

  private AppPayParameters parameters() {
    return new AppPayParameters(
        "wx-app", "merchant", "prepay", "Sign=WXPay", "nonce", "123456", "signature");
  }

  private NotificationHeaders headers() {
    return new NotificationHeaders(
        "serial", "timestamp", "nonce", "signature", "WECHATPAY2-SHA256-RSA2048");
  }

  private AppUser user(Long id) {
    AppUser user = new AppUser("13800000001", "hash", "用户", UserRole.CUSTOMER);
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }
}
