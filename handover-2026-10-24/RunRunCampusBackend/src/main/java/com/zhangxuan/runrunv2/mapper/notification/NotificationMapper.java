package com.zhangxuan.runrunv2.mapper.notification;

import com.zhangxuan.runrunv2.config.SingleRowRequired;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** 站内通知与 outbox持久化映射；事务、锁顺序及业务校验由原调用方保持。 */
@Mapper
public interface NotificationMapper {

    List<Map<String, Object>> findUserNotifications(@Param("userId") long userId);

    int markNotificationRead(
            @Param("notificationId") long notificationId, @Param("userId") long userId);

    List<Map<String, Object>> claimOutboxDeliveries(@Param("limit") int limit);

    @SingleRowRequired
    Object completeDelivery(
            @Param("id") long id, @Param("success") boolean success, @Param("error") String error);
}
