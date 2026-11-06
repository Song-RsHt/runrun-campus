package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.enums.CourierCertificationStatus;
import com.zhangxuan.runruncampus.vo.AdminCourierPageResponse;
import com.zhangxuan.runruncampus.vo.AdminCourierPerformanceResponse;

/** 管理员骑手查询业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface AdminCourierService {

    /**
     * 查询管理员骑手查询列表，保留现有筛选和分页口径。
     *
     * @param query 可选搜索关键词
     * @param status 可选业务状态筛选
     * @param page 从零开始的分页页码
     * @param size 每页记录数量
     * @return 管理员骑手查询分页
     */
    AdminCourierPageResponse list(
            String query, CourierCertificationStatus status, int page, int size);

    /**
     * 查询骑手履约统计及订单分页。
     *
     * @param courierId 目标骑手标识
     * @param page 从零开始的分页页码
     * @param size 每页记录数量
     * @return 管理员骑手查询履约统计
     */
    AdminCourierPerformanceResponse performance(Long courierId, int page, int size);
}
