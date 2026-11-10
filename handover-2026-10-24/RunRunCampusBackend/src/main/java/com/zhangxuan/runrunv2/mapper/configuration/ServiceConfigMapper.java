package com.zhangxuan.runrunv2.mapper.configuration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhangxuan.runrunv2.config.SingleRowRequired;
import com.zhangxuan.runrunv2.entity.configuration.DeliveryZoneEntity;
import com.zhangxuan.runrunv2.vo.order.DeliveryZoneVO;
import com.zhangxuan.runrunv2.vo.order.ServicePointVO;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 服务配置持久化映射；事务、锁顺序及业务校验由原调用方保持。 */
@Mapper
public interface ServiceConfigMapper extends BaseMapper<DeliveryZoneEntity> {

    @SingleRowRequired
    Map<String, Object> lockSettings(@Param("forUpdate") boolean forUpdate);

    default List<DeliveryZoneVO> findZones(boolean includeDisabled) {
        return selectList(
                        new LambdaQueryWrapper<DeliveryZoneEntity>()
                                .eq(!includeDisabled, DeliveryZoneEntity::getEnabled, true)
                                .orderByAsc(DeliveryZoneEntity::getId))
                .stream()
                .map(
                        zone ->
                                new DeliveryZoneVO(
                                        zone.getId().toString(),
                                        zone.getCode(),
                                        zone.getName(),
                                        zone.getPackageSurchargeCents(),
                                        zone.getEnabled(),
                                        zone.getConfigVersion(),
                                        zone.getVersion()))
                .toList();
    }

    List<ServicePointVO> findServicePoints(@Param("includeDisabled") boolean includeDisabled);

    default List<Map<String, Object>> findEnabledZone(String code) {
        return selectMaps(
                new LambdaQueryWrapper<DeliveryZoneEntity>()
                        .eq(DeliveryZoneEntity::getCode, code)
                        .eq(DeliveryZoneEntity::getEnabled, true));
    }

    List<Map<String, Object>> findEnabledServicePoint(@Param("code") String code);

    int upsertZone(
            @Param("code") String code,
            @Param("name") String name,
            @Param("packageSurchargeCents") Long packageSurchargeCents,
            @Param("enabled") Boolean enabled,
            @Param("configVersion") long configVersion);

    int upsertServicePoint(
            @Param("code") String code,
            @Param("name") String name,
            @Param("latitude") BigDecimal latitude,
            @Param("longitude") BigDecimal longitude,
            @Param("coordinateSystem") String coordinateSystem,
            @Param("enabled") Boolean enabled,
            @Param("configVersion") long configVersion);

    int updateSettings(
            @Param("enabled") boolean enabled, @Param("reviewOwnerId") Long reviewOwnerId);

    int insertConfigChangeAudit(
            @Param("administratorId") Long administratorId,
            @Param("requestId") String requestId,
            @Param("reason") String reason,
            @Param("metadata") String metadata);

    @SingleRowRequired
    Boolean isEligibleReviewOwner(@Param("userId") Long userId);
}
