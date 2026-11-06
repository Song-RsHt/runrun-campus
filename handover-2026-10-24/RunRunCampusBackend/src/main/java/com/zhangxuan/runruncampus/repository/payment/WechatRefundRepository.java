package com.zhangxuan.runruncampus.repository.payment;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.enums.payment.RefundStatus;
import com.zhangxuan.runruncampus.model.order.CampusOrder;
import com.zhangxuan.runruncampus.model.payment.WechatPayment;
import com.zhangxuan.runruncampus.model.payment.WechatRefund;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;















public interface WechatRefundRepository extends JpaRepository<WechatRefund, Long> {

  List<WechatRefund> findTop20ByStatusOrderByCreatedAtAsc(RefundStatus status);

  List<WechatRefund> findByPaymentOrderByCreatedAtAsc(WechatPayment payment);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from WechatRefund r where r.outRefundNo = :outRefundNo")
  Optional<WechatRefund> findLockedByOutRefundNo(@Param("outRefundNo") String outRefundNo);

  @Query(
      "select coalesce(sum(r.amountCents), 0) from WechatRefund r where r.payment.order = :order"
          + " and r.status = com.zhangxuan.runruncampus.enums.payment.RefundStatus.SUCCESS")
  long sumSucceededForOrder(@Param("order") CampusOrder order);

  @Query("select r from WechatRefund r where r.payment.order = :order order by r.createdAt asc")
  List<WechatRefund> findByOrder(@Param("order") CampusOrder order);

  @Query(
      "select count(r) > 0 from WechatRefund r where r.payment.user = :user "
          + "and r.status in (com.zhangxuan.runruncampus.enums.payment.RefundStatus.PENDING, "
          + "com.zhangxuan.runruncampus.enums.payment.RefundStatus.PROCESSING)")
  boolean existsUnfinishedByUser(@Param("user") AppUser user);
}
