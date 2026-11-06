package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.enums.OrderStatus;
import com.zhangxuan.runruncampus.model.OrderExport;
import com.zhangxuan.runruncampus.vo.AdminOrderDetailResponse;
import com.zhangxuan.runruncampus.vo.AdminOrderPageResponse;































/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface AdminOrderService {

    AdminOrderPageResponse list(String query, OrderStatus status, boolean archived, int page, int size);

    AdminOrderDetailResponse detail(Long id);

    OrderExport exportCsv(String query, OrderStatus status, boolean archived);
}
