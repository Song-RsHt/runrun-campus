package com.zhangxuan.runrunv2.service;

import com.zhangxuan.runrunv2.dto.CancelRequest;
import com.zhangxuan.runrunv2.dto.PaymentResultRequest;
import com.zhangxuan.runrunv2.dto.PaymentVersionRequest;
import com.zhangxuan.runrunv2.vo.AdjustmentFundsVO;
import com.zhangxuan.runrunv2.vo.CancelledOrderVO;
import com.zhangxuan.runrunv2.vo.OriginalPaymentVO;
import com.zhangxuan.runrunv2.vo.PaymentAttemptVO;
import com.zhangxuan.runrunv2.vo.PaymentSummaryVO;
import com.zhangxuan.runrunv2.vo.RefundOrderVO;
import com.zhangxuan.runrunv2.vo.StartedPaymentVO;

import java.time.OffsetDateTime;

/** 支付回执及退款业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface PaymentService extends OriginalPaymentWriter {

    /**
     * 报告原始支付写入能力是否已装配可用。
     *
     * @return 原始支付原子写入器是否可用
     */
    boolean available();

    /**
     * 在调用方订单事务内写入原始支付义务，不单独提交事务。
     *
     * <p>不能独立提交；订单和原始支付义务必须一起成功或回滚。
     *
     * @param orderId 订单标识
     * @param amountCents 原始支付金额，单位分
     * @param deadline 原订单支付截止时刻
     * @return 与订单原子写入的原始支付义务，包含金额、截止时刻及版本
     */
    OriginalPaymentVO create(String orderId, long amountCents, OffsetDateTime deadline);

    /**
     * 读取当前用户订单的支付义务、尝试及退款记录。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param paymentId 支付义务标识
     * @return 支付义务、历次支付尝试和退款记录
     */
    PaymentSummaryVO get(String token, String paymentId);

    /**
     * 创建或复用支付尝试；存在未对账 UNKNOWN 尝试时拒绝重复扣款。
     *
     * <p>UNKNOWN 结果包括传输超时；未完成原尝试对账时不得发起第二次扣款。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param paymentId 支付义务标识
     * @param key 本次操作的幂等键，作用域由现有业务规则决定
     * @param body 支付或退款的预期并发版本
     * @return 本次发起结果及对应的支付尝试
     */
    StartedPaymentVO start(String token, String paymentId, String key, PaymentVersionRequest body);

    /**
     * 查询原渠道支付结果，迟到成功回执仍须补偿。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param id 支付义务标识
     * @param key 本次操作的幂等键，作用域由现有业务规则决定
     * @param body 支付或退款的预期并发版本
     * @return 支付义务、历次支付尝试和退款记录
     */
    PaymentSummaryVO queryPayment(String token, String id, String key, PaymentVersionRequest body);

    /**
     * 受控写入模拟支付回执，通过统一支付锁路径处理。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param id 支付尝试标识
     * @param key 本次操作的幂等键，作用域由现有业务规则决定
     * @param body 模拟渠道结果及预期版本
     * @return 支付尝试及渠道处理状态
     */
    PaymentAttemptVO paymentResult(String token, String id, String key, PaymentResultRequest body);

    /**
     * 受控写入模拟退款回执，保留退款预留余额。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param id 退款记录标识
     * @param key 本次操作的幂等键，作用域由现有业务规则决定
     * @param body 模拟渠道结果及预期版本
     * @return 退款订单
     */
    RefundOrderVO refundResult(String token, String id, String key, PaymentResultRequest body);

    /**
     * 查询原退款渠道身份，不新建退款转账。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param id 退款记录标识
     * @param key 本次操作的幂等键，作用域由现有业务规则决定
     * @param body 支付或退款的预期并发版本
     * @return 退款订单
     */
    RefundOrderVO queryRefund(String token, String id, String key, PaymentVersionRequest body);

    /**
     * 按原取消规则关闭订单及资金义务，保存退款和事件。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param id 订单标识
     * @param key 本次操作的幂等键，作用域由现有业务规则决定
     * @param body 取消原因及预期订单版本
     * @return 已取消订单
     */
    CancelledOrderVO cancel(String token, String id, String key, CancelRequest body);

    /**
     * 对 FAILED 退款复用原记录及渠道身份重试，UNKNOWN 仅查询。
     *
     * <p>由可信任务或已完成确认策略的管理员调用；失败重试复用原退款记录，未知结果先查询。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param id 退款记录标识
     * @param expectedVersion 调用方看到的并发版本，更新必须与当前版本一致
     * @param reason 业务操作理由，按原有长度和必填规则校验
     * @param key 本次操作的幂等键，作用域由现有业务规则决定
     * @return 退款订单
     */
    RefundOrderVO retryRefund(
            String token, String id, long expectedVersion, String reason, String key);

    /**
     * 在调用方审核事务内登记已确认价差，金额仅来自服务端审核计价。
     *
     * <p>调用方须持有审核锁并使用同一事务；金额只能来自服务端已审核的计价和证据。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param orderId 订单标识
     * @param adjustmentId 已审核规格调整标识
     * @param deltaCents 服务端审核确认的价差，单位分
     * @param expectedOrderVersion 调用方持有的订单并发版本
     * @param reason 业务操作理由，按原有长度和必填规则校验
     * @param key 本次操作的幂等键，作用域由现有业务规则决定
     * @return 调整资金
     */
    AdjustmentFundsVO approvedAdjustment(
            String token,
            String orderId,
            String adjustmentId,
            long deltaCents,
            long expectedOrderVersion,
            String reason,
            String key);

    /**
     * 按有界批次关闭超时支付，复用回调所用事务与锁路径。
     *
     * @return 本批成功关闭的到期支付数量
     */
    int closeExpired();
}
