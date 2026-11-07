package com.zhangxuan.runrunv2.mapper.payment;

import com.zhangxuan.runrunv2.config.SingleRowRequired;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** 支付与退款持久化映射；事务、锁顺序及业务校验由原调用方保持。 */
@Mapper
public interface MockPaymentMapper {

    int insertProcessingResult(
            @Param("resourceType") String resourceType, @Param("resourceId") Long resourceId);

    List<Map<String, Object>> findResult(
            @Param("resourceType") String resourceType, @Param("resourceId") Long resourceId);

    @SingleRowRequired
    Long findResultVersion(
            @Param("resourceType") String resourceType, @Param("resourceId") Long resourceId);

    int updateResultUnlessSuccessful(
            @Param("result") String result,
            @Param("resourceType") String resourceType,
            @Param("resourceId") Long resourceId);

    int retryFailedRefund(@Param("refundId") Long refundId);
}
