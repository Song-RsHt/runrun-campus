package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.CampusOrder;
import com.zhangxuan.runruncampus.enums.MembershipPlanType;
import com.zhangxuan.runruncampus.model.NotificationHeaders;
import com.zhangxuan.runruncampus.vo.PaymentResponse;
import com.zhangxuan.runruncampus.vo.RefundResponse;

import java.util.List;

/** 支付回执及退款业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface PaymentService {

    /**
     * 为用户订单创建或复用既有支付记录。
     *
     * @param customer 当前用户账号
     * @param orderId 订单标识
     * @return 支付回执及退款
     */
    PaymentResponse createOrderPayment(AppUser customer, Long orderId);

    /**
     * 为会员套餐购买创建支付记录。
     *
     * @param customer 当前用户账号
     * @param plan 会员套餐类型
     * @return 支付回执及退款
     */
    PaymentResponse createMembershipPayment(AppUser customer, MembershipPlanType plan);

    /**
     * 查询支付回执及退款当前状态。
     *
     * @param customer 当前用户账号
     * @param outTradeNo 服务端生成的渠道交易单号
     * @return 支付回执及退款
     */
    PaymentResponse status(AppUser customer, String outTradeNo);

    /**
     * 校验渠道通知并幂等推进付款事实。
     *
     * @param headers 渠道通知验签所需的协议元数据
     * @param body 已按入口契约绑定的业务参数
     */
    void acceptPaymentNotification(NotificationHeaders headers, String body);

    /**
     * 校验渠道通知并幂等推进退款事实。
     *
     * @param headers 渠道通知验签所需的协议元数据
     * @param body 已按入口契约绑定的业务参数
     */
    void acceptRefundNotification(NotificationHeaders headers, String body);

    /**
     * 订单取消时关闭支付并按实际收款发起退款。
     *
     * @param order 调用方已读取并按原规则保护的订单
     */
    void closeAndRefundForCancellation(CampusOrder order);

    /**
     * 对已收款订单退还确认后的价差。
     *
     * @param order 调用方已读取并按原规则保护的订单
     * @param refundCents 退款金额，单位分
     */
    void refundPriceDifference(CampusOrder order, int refundCents);

    /**
     * 查询订单的退款记录。
     *
     * @param order 调用方已读取并按原规则保护的订单
     * @return 退款列表，顺序和筛选沿用原查询
     */
    List<RefundResponse> refundsForOrder(CampusOrder order);
}
