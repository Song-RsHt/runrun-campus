package com.zhangxuan.runruncampus.service.impl;

import com.zhangxuan.runruncampus.constant.AdminCommandCenterConstants;
import com.zhangxuan.runruncampus.enums.AdminPermission;
import com.zhangxuan.runruncampus.enums.AdminRole;
import com.zhangxuan.runruncampus.enums.Queue;
import com.zhangxuan.runruncampus.exception.BusinessFailure;
import com.zhangxuan.runruncampus.mapper.AdminCommandCenterMapper;
import com.zhangxuan.runruncampus.service.AdminCommandCenterService;
import com.zhangxuan.runruncampus.service.PlatformSettingsService;
import com.zhangxuan.runruncampus.vo.CommandCenterCouriersVO;
import com.zhangxuan.runruncampus.vo.CommandCenterOrdersVO;
import com.zhangxuan.runruncampus.vo.CommandCenterSnapshotVO;
import com.zhangxuan.runruncampus.vo.CourierItem;
import com.zhangxuan.runruncampus.vo.OrderItem;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * 后台指挥台业务实现；通过注入的业务契约及数据访问组件执行，事务边界保留在本类。
 *
 * <p>按权限组装运营队列快照。重复读事务确保计数及分页来自一致视图；SQL 和行映射集中在 Mapper，分页裁剪由本层决定。
 */
@Service
public class AdminCommandCenterServiceImpl implements AdminCommandCenterService {

    private final AdminCommandCenterMapper mapper;
    private final PlatformSettingsService settingsService;

    public AdminCommandCenterServiceImpl(
            AdminCommandCenterMapper mapper, PlatformSettingsService settingsService) {
        this.mapper = mapper;
        this.settingsService = settingsService;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @Override
    public CommandCenterSnapshotVO snapshot(
            AdminRole role, Queue queue, int orderPage, int courierPage) {
        if (orderPage < 0 || courierPage < 0 || orderPage > 100000 || courierPage > 100000) {
            throw new BusinessFailure(400, "页码超出范围");
        }
        Instant now = Instant.now();
        var settings = settingsService.get();
        CommandCenterOrdersVO orders =
                role.permits(AdminPermission.ORDER_VIEW)
                        ? orders(
                                queue,
                                orderPage,
                                now,
                                settings.waitMinutes(),
                                settings.deliveryMinutes())
                        : null;
        CommandCenterCouriersVO couriers =
                role.permits(AdminPermission.COURIER_VIEW) ? couriers(courierPage) : null;
        return new CommandCenterSnapshotVO(
                now, settings.waitMinutes(), settings.deliveryMinutes(), orders, couriers);
    }

    private CommandCenterOrdersVO orders(
            Queue queue, int requestedPage, Instant now, int waitMinutes, int deliveryMinutes) {
        Timestamp waitCutoff = Timestamp.from(now.minusSeconds(waitMinutes * 60L));
        Timestamp deliveryCutoff = Timestamp.from(now.minusSeconds(deliveryMinutes * 60L));
        long[] counts = mapper.countOrderQueues(waitCutoff, deliveryCutoff);
        long total = counts[queue.ordinal()];
        int page = clampPage(requestedPage, total);
        List<OrderItem> items =
                mapper.pageOrderQueue(
                        queue,
                        waitCutoff,
                        deliveryCutoff,
                        AdminCommandCenterConstants.PAGE_SIZE,
                        page * AdminCommandCenterConstants.PAGE_SIZE);
        return new CommandCenterOrdersVO(
                counts[0],
                counts[1],
                counts[2],
                counts[3],
                queue,
                items,
                page,
                AdminCommandCenterConstants.PAGE_SIZE,
                total);
    }

    private CommandCenterCouriersVO couriers(int requestedPage) {
        long[] counts = mapper.countOnlineCouriers();
        int page = clampPage(requestedPage, counts[0]);
        List<CourierItem> items =
                mapper.pageOnlineCouriers(
                        AdminCommandCenterConstants.PAGE_SIZE,
                        page * AdminCommandCenterConstants.PAGE_SIZE);
        return new CommandCenterCouriersVO(
                counts[0], counts[1], items, page, AdminCommandCenterConstants.PAGE_SIZE);
    }

    private int clampPage(int page, long total) {
        return (int)
                Math.min(page, Math.max(0, (total - 1) / AdminCommandCenterConstants.PAGE_SIZE));
    }
}
