package com.zhangxuan.runruncampus.service.admin;

import com.zhangxuan.runruncampus.enums.admin.AdminRole;
import com.zhangxuan.runruncampus.enums.commandcenter.Queue;
import com.zhangxuan.runruncampus.vo.commandcenter.CommandCenterSnapshotVO;

/** 后台指挥台业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface AdminCommandCenterService {

    /**
     * 按管理员角色查询订单和骑手队列快照。
     *
     * @param role 业务角色或管理员岗位
     * @param queue 指挥台队列选择
     * @param orderPage 订单队列页码
     * @param courierPage 骑手队列页码
     * @return 指挥台业务快照
     */
    CommandCenterSnapshotVO snapshot(AdminRole role, Queue queue, int orderPage, int courierPage);
}
