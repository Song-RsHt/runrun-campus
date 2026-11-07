package com.zhangxuan.runrunv2.mapper.notification;

import com.zhangxuan.runrunv2.config.SingleRowRequired;
import com.zhangxuan.runrunv2.model.notification.Delivery;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 站内通知与 outbox持久化映射；事务、锁顺序及业务校验由原调用方保持。 */
@Mapper
public interface NotificationOutboxMapper {

    List<Delivery> claimOutboxDeliveries(@Param("limit") int limit);

    @SingleRowRequired
    Object completeDelivery(
            @Param("outboxId") long outboxId,
            @Param("success") boolean success,
            @Param("error") String error);

    @SingleRowRequired
    Integer runReviewTimers();

    @SingleRowRequired
    Boolean isNotificationRuntimeInstalled();
}
