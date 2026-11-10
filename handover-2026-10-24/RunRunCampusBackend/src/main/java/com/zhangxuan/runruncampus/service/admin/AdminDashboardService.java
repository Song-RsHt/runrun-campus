package com.zhangxuan.runruncampus.service.admin;

import com.zhangxuan.runruncampus.vo.admin.AdminDashboardResponse;

/** 后台业务概览业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface AdminDashboardService {

    /**
     * 查询后台业务概览概览。
     *
     * @return 后台业务概览
     */
    AdminDashboardResponse overview();
}
