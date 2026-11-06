package com.zhangxuan.runruncampus.service.admin;

import com.zhangxuan.runruncampus.dto.response.commandcenter.CourierItem;
import com.zhangxuan.runruncampus.dto.response.commandcenter.Couriers;
import com.zhangxuan.runruncampus.dto.response.commandcenter.OrderItem;
import com.zhangxuan.runruncampus.dto.response.commandcenter.Orders;
import com.zhangxuan.runruncampus.dto.response.commandcenter.Snapshot;
import com.zhangxuan.runruncampus.enums.admin.AdminPermission;
import com.zhangxuan.runruncampus.enums.admin.AdminRole;
import com.zhangxuan.runruncampus.enums.commandcenter.Queue;
import com.zhangxuan.runruncampus.mapper.admin.AdminCommandCenterMapper;
import com.zhangxuan.runruncampus.service.api.PlatformSettingsService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 按权限组装运营队列快照。重复读事务确保计数及分页来自一致视图；SQL 和行映射集中在 Mapper，分页裁剪由本层决定。 */
@Service
public class AdminCommandCenterService {

  public static final int PAGE_SIZE = 20;

  private final AdminCommandCenterMapper mapper;

  private final PlatformSettingsService settingsService;

  public AdminCommandCenterService(
      AdminCommandCenterMapper mapper, PlatformSettingsService settingsService) {
    this.mapper = mapper;
    this.settingsService = settingsService;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Snapshot snapshot(AdminRole role, Queue queue, int orderPage, int courierPage) {
    if (orderPage < 0 || courierPage < 0 || orderPage > 100000 || courierPage > 100000) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "页码超出范围");
    }
    Instant now = Instant.now();
    var settings = settingsService.get();
    Orders orders =
        role.permits(AdminPermission.ORDER_VIEW)
            ? orders(queue, orderPage, now, settings.waitMinutes(), settings.deliveryMinutes())
            : null;
    Couriers couriers = role.permits(AdminPermission.COURIER_VIEW) ? couriers(courierPage) : null;
    return new Snapshot(now, settings.waitMinutes(), settings.deliveryMinutes(), orders, couriers);
  }

  private Orders orders(
      Queue queue, int requestedPage, Instant now, int waitMinutes, int deliveryMinutes) {
    Timestamp waitCutoff = Timestamp.from(now.minusSeconds(waitMinutes * 60L));
    Timestamp deliveryCutoff = Timestamp.from(now.minusSeconds(deliveryMinutes * 60L));
    long[] counts = mapper.countOrderQueues(waitCutoff, deliveryCutoff);
    long total = counts[queue.ordinal()];
    int page = clampPage(requestedPage, total);
    List<OrderItem> items =
        mapper.pageOrderQueue(queue, waitCutoff, deliveryCutoff, PAGE_SIZE, page * PAGE_SIZE);
    return new Orders(
        counts[0], counts[1], counts[2], counts[3], queue, items, page, PAGE_SIZE, total);
  }

  private Couriers couriers(int requestedPage) {
    long[] counts = mapper.countOnlineCouriers();
    int page = clampPage(requestedPage, counts[0]);
    List<CourierItem> items = mapper.pageOnlineCouriers(PAGE_SIZE, page * PAGE_SIZE);
    return new Couriers(counts[0], counts[1], items, page, PAGE_SIZE);
  }

  private int clampPage(int page, long total) {
    return (int) Math.min(page, Math.max(0, (total - 1) / PAGE_SIZE));
  }
}
