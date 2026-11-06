package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.enums.OrderStatus;
import com.zhangxuan.runruncampus.model.OrderExport;
import com.zhangxuan.runruncampus.vo.AdminOrderDetailResponse;
import com.zhangxuan.runruncampus.vo.AdminOrderPageResponse;

/** 管理员订单查询与导出业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface AdminOrderService {

    /**
     * 查询管理员订单查询与导出列表，保留现有筛选和分页口径。
     *
     * @param query 可选搜索关键词
     * @param status 可选业务状态筛选
     * @param archived 是否筛选归档订单
     * @param page 从零开始的分页页码
     * @param size 每页记录数量
     * @return 管理员订单查询与导出分页
     */
    AdminOrderPageResponse list(
            String query, OrderStatus status, boolean archived, int page, int size);

    /**
     * 读取管理员订单详情及事件、凭证信息。
     *
     * @param id 目标业务记录标识
     * @return 管理员订单查询与导出详情
     */
    AdminOrderDetailResponse detail(Long id);

    /**
     * 按原有筛选和数量上限生成订单 CSV 及截断标记。
     *
     * @param query 可选搜索关键词
     * @param status 可选业务状态筛选
     * @param archived 是否筛选归档订单
     * @return CSV 文件内容及是否达到导出上限的标记
     */
    OrderExport exportCsv(String query, OrderStatus status, boolean archived);
}
