package com.zhangxuan.runrunv2.vo.payment;

import com.zhangxuan.runrunv2.vo.order.OrderVO;

import java.util.List;

/** 已取消订单响应；保持现有 JSON 字段及序列化契约。 */
public record CancelledOrderVO(OrderVO order, List<RefundOrderVO> refunds) {}
