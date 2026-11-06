package com.zhangxuan.runruncampus.constant;

import com.zhangxuan.runruncampus.enums.AdminPermission;
import com.zhangxuan.runruncampus.enums.AdminRole;
import com.zhangxuan.runruncampus.enums.Queue;
import com.zhangxuan.runruncampus.mapper.AdminCommandCenterMapper;
import com.zhangxuan.runruncampus.service.PlatformSettingsService;
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

public final class AdminCommandCenterConstants {

    private AdminCommandCenterConstants() {
    }

    public static final int PAGE_SIZE = 20;
}
