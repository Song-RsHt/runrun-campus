package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.constant.AdminCommandCenterConstants;
import com.zhangxuan.runruncampus.enums.AdminPermission;
import com.zhangxuan.runruncampus.enums.AdminRole;
import com.zhangxuan.runruncampus.enums.Queue;
import com.zhangxuan.runruncampus.mapper.AdminCommandCenterMapper;
import com.zhangxuan.runruncampus.vo.CourierItem;
import com.zhangxuan.runruncampus.vo.Couriers;
import com.zhangxuan.runruncampus.vo.OrderItem;
import com.zhangxuan.runruncampus.vo.Orders;
import com.zhangxuan.runruncampus.vo.Snapshot;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface AdminCommandCenterService {

    Snapshot snapshot(AdminRole role, Queue queue, int orderPage, int courierPage);
}
