package com.zhangxuan.runruncampus.service.admin;

import com.zhangxuan.runruncampus.enums.message.PushDeliveryStatus;
import com.zhangxuan.runruncampus.vo.admin.PushDeliveryOverview;

/** 管理员推送投递监控业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface AdminPushDeliveryService {

    /**
     * 查询管理员推送投递监控概览。
     *
     * @param status 可选业务状态筛选
     * @param page 从零开始的分页页码
     * @param size 每页记录数量
     * @return 推送投递概览
     */
    PushDeliveryOverview overview(PushDeliveryStatus status, int page, int size);
}
