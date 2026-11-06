package com.zhangxuan.runruncampus.service.payment;

import com.zhangxuan.runruncampus.entity.user.AppUser;
import com.zhangxuan.runruncampus.vo.payment.ManualPaymentResponse;

/** 人工扫码付款与骑手结算业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface ManualQrPaymentService {

    /**
     * 骑手到达后发出人工扫码付款通知。
     *
     * @param courier 当前骑手账号或标识
     * @param orderId 订单标识
     * @return 人工支付
     */
    ManualPaymentResponse requestPayment(AppUser courier, Long orderId);

    /**
     * 用户提交人工付款声明，进入管理员核对流程。
     *
     * @param customer 当前用户账号
     * @param orderId 订单标识
     * @param payerReference 付款账号备注，不作为渠道收款事实
     * @return 人工支付
     */
    ManualPaymentResponse submitPayment(AppUser customer, Long orderId, String payerReference);

    /**
     * 管理员核实人工付款并推进原有订单状态。
     *
     * @param administrator 已通过权限校验的管理员
     * @param orderId 订单标识
     * @return 人工支付
     */
    ManualPaymentResponse confirmPayment(AppUser administrator, Long orderId);

    /**
     * 管理员驳回人工付款声明并记录原因。
     *
     * @param administrator 已通过权限校验的管理员
     * @param orderId 订单标识
     * @param reason 业务操作理由，按原有长度和必填规则校验
     * @return 人工支付
     */
    ManualPaymentResponse rejectPayment(AppUser administrator, Long orderId, String reason);

    /**
     * 管理员登记已完成订单的骑手结算。
     *
     * @param administrator 已通过权限校验的管理员
     * @param orderId 订单标识
     * @return 人工支付
     */
    ManualPaymentResponse confirmCourierSettlement(AppUser administrator, Long orderId);

    /**
     * 查询人工扫码付款与骑手结算当前状态。
     *
     * @param participant 当前订单参与者
     * @param orderId 订单标识
     * @return 人工支付
     */
    ManualPaymentResponse status(AppUser participant, Long orderId);
}
