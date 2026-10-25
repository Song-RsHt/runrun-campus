package com.zhangxuan.runruncampus.payment;

import com.zhangxuan.runruncampus.order.CampusOrder;
import com.zhangxuan.runruncampus.user.AppUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WechatPaymentRepository extends JpaRepository<WechatPayment, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from WechatPayment p where p.outTradeNo = :outTradeNo")
    Optional<WechatPayment> findLockedByOutTradeNo(@Param("outTradeNo") String outTradeNo);

    Optional<WechatPayment> findByOutTradeNo(String outTradeNo);
    List<WechatPayment> findByOrderAndStatusOrderByCreatedAtAsc(CampusOrder order, PaymentStatus status);
    Optional<WechatPayment> findFirstByOrderAndStatusOrderByCreatedAtDesc(CampusOrder order, PaymentStatus status);
    Optional<WechatPayment> findFirstByUserAndBusinessTypeAndStatusOrderByCreatedAtDesc(
            AppUser user, PaymentBusinessType businessType, PaymentStatus status);

    @Query("select coalesce(sum(p.amountCents), 0) from WechatPayment p where p.order = :order and p.status = com.zhangxuan.runruncampus.payment.PaymentStatus.PAID")
    long sumPaidForOrder(@Param("order") CampusOrder order);
}
