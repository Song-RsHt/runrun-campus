package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.enums.AdminRole;
import com.zhangxuan.runruncampus.enums.Queue;
import com.zhangxuan.runruncampus.vo.Snapshot;





















/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface AdminCommandCenterService {

    Snapshot snapshot(AdminRole role, Queue queue, int orderPage, int courierPage);
}
