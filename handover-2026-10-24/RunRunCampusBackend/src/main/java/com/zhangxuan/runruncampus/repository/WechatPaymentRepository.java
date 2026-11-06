package com.zhangxuan.runruncampus.repository;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.CampusOrder;
import com.zhangxuan.runruncampus.entity.WechatPayment;
import com.zhangxuan.runruncampus.enums.PaymentBusinessType;
import com.zhangxuan.runruncampus.enums.PaymentStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** 微信支付 JPA 仓储；只定义持久化查询和写入，不承担权限及业务决策。 */
public interface WechatPaymentRepository extends JpaRepository<WechatPayment, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from WechatPayment p where p.outTradeNo = :outTradeNo")
    Optional<WechatPayment> findLockedByOutTradeNo(@Param("outTradeNo") String outTradeNo);

    Optional<WechatPayment> findByOutTradeNo(String outTradeNo);

    List<WechatPayment> findByOrderAndStatusOrderByCreatedAtAsc(
            CampusOrder order, PaymentStatus status);

    Optional<WechatPayment> findFirstByOrderAndStatusOrderByCreatedAtDesc(
            CampusOrder order, PaymentStatus status);

    Optional<WechatPayment> findFirstByUserAndBusinessTypeAndStatusOrderByCreatedAtDesc(
            AppUser user, PaymentBusinessType businessType, PaymentStatus status);

    @Query(
            "select coalesce(sum(p.amountCents), 0) from WechatPayment p where p.order = :order and"
                    + " p.status = com.zhangxuan.runruncampus.enums.PaymentStatus.PAID")
    long sumPaidForOrder(@Param("order") CampusOrder order);
}
