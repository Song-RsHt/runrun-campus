package com.zhangxuan.runruncampus.service.courier;

import com.zhangxuan.runruncampus.entity.user.AppUser;
import com.zhangxuan.runruncampus.vo.courier.OperationsResponse;

/** 骑手接单开关业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface CourierOperationsService {

    /**
     * 查询骑手接单开关概览。
     *
     * @param courier 当前骑手账号或标识
     * @return 接单管理
     */
    OperationsResponse overview(AppUser courier);

    /**
     * 切换骑手接单开关，上线前校验资质和账号状态。
     *
     * @param courier 当前骑手账号或标识
     * @param acceptingOrders 是否开启骑手接单
     * @return 接单管理
     */
    OperationsResponse updateAvailability(AppUser courier, boolean acceptingOrders);
}
