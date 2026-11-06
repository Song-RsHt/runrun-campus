package com.zhangxuan.runrunv2.service;

import com.zhangxuan.runrunv2.dto.FulfillmentDeliverRequest;
import com.zhangxuan.runrunv2.dto.FulfillmentLocationRequest;
import com.zhangxuan.runrunv2.dto.FulfillmentPickupRequest;
import com.zhangxuan.runrunv2.dto.FulfillmentReleaseRequest;
import com.zhangxuan.runrunv2.dto.FulfillmentRespondRequest;
import com.zhangxuan.runrunv2.dto.FulfillmentUploadRequest;
import com.zhangxuan.runrunv2.dto.FulfillmentVersionRequest;
import com.zhangxuan.runrunv2.dto.RegisterFulfillmentProofRequest;
import com.zhangxuan.runrunv2.vo.FulfillmentOrderVO;
import com.zhangxuan.runrunv2.vo.FulfillmentResultVO;
import com.zhangxuan.runrunv2.vo.FulfillmentTaskVO;
import com.zhangxuan.runrunv2.vo.LocationEventVO;
import com.zhangxuan.runrunv2.vo.OrderProofVO;
import com.zhangxuan.runrunv2.vo.UploadTicketVO;

/** 订单履约业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface FulfillmentService {

    /**
     * 校验接单资格、名额和订单状态后领取任务。
     *
     * <p>写入前锁骑手账号并检查名额；不满足接单资格、状态或版本时拒绝。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param orderId 订单标识
     * @param key 本次操作的幂等键，作用域由现有业务规则决定
     * @param body 调用方持有的订单或任务并发版本
     * @return 订单履约结果
     */
    FulfillmentResultVO accept(
            String token, String orderId, String key, FulfillmentVersionRequest body);

    /**
     * 受控创建新的骑手邀请，禁止重新激活历史任务。
     *
     * <p>调用方须已有受审计的异常或重新履约决定；旧任务不能恢复为有效任务。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param orderId 订单标识
     * @param courierId 目标骑手标识
     * @param expectedVersion 调用方看到的并发版本，更新必须与当前版本一致
     * @param reason 业务操作理由，按原有长度和必填规则校验
     * @param key 本次操作的幂等键，作用域由现有业务规则决定
     * @return 订单履约任务
     */
    FulfillmentTaskVO invite(
            String token,
            String orderId,
            String courierId,
            long expectedVersion,
            String reason,
            String key);

    /**
     * 骑手接受或拒绝邀请，接受时重新校验资格和并发状态。
     *
     * <p>拒绝邀请不要求接单资格，接受必须检查当前资格和名额。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param taskId 履约任务标识
     * @param key 本次操作的幂等键，作用域由现有业务规则决定
     * @param body 接受或拒绝邀请的决定及预期版本
     * @return 订单履约结果
     */
    FulfillmentResultVO respond(
            String token, String taskId, String key, FulfillmentRespondRequest body);

    /**
     * 校验任务可释放且不存在实际保管不确定性后释放骑手名额并记录事件。
     *
     * <p>存在取件照片所代表的保管不确定性时不能走普通自行释放。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param orderId 订单标识
     * @param key 本次操作的幂等键，作用域由现有业务规则决定
     * @param body 释放原因及预期版本
     * @return 订单履约结果
     */
    FulfillmentResultVO release(
            String token, String orderId, String key, FulfillmentReleaseRequest body);

    /**
     * 校验当前履约写权限后申请私有上传票据，重放仍复核票据有效期。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param orderId 订单标识
     * @param key 本次操作的幂等键，作用域由现有业务规则决定
     * @param body 上传用途、文件元数据及预期版本
     * @return 上传票据
     */
    UploadTicketVO upload(String token, String orderId, String key, FulfillmentUploadRequest body);

    /**
     * 将私有证据票据登记到指定订单与任务。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param orderId 订单标识
     * @param key 本次操作的幂等键，作用域由现有业务规则决定
     * @param body 登记履约凭证
     * @return 订单私有图片凭证
     */
    OrderProofVO proof(
            String token, String orderId, String key, RegisterFulfillmentProofRequest body);

    /**
     * 保存骑手到达事件，包括被拒绝或未配置的服务点，重放只读取原事件。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param orderId 订单标识
     * @param key 本次操作的幂等键，作用域由现有业务规则决定
     * @param body 位置事件类型、坐标、任务标识及预期版本
     * @return 位置事件
     */
    LocationEventVO location(
            String token, String orderId, String key, FulfillmentLocationRequest body);

    /**
     * 核对取件证据并推进任务及订单取件状态。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param orderId 订单标识
     * @param key 本次操作的幂等键，作用域由现有业务规则决定
     * @param body 取件凭证标识及预期版本
     * @return 订单履约结果
     */
    FulfillmentResultVO pickup(
            String token, String orderId, String key, FulfillmentPickupRequest body);

    /**
     * 记录送达并释放骑手名额，资金和审核条件继续约束完成确认。
     *
     * <p>送达和释放骑手名额原子提交，资金或审核阻塞不能继续占用名额。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param orderId 订单标识
     * @param key 本次操作的幂等键，作用域由现有业务规则决定
     * @param body 送达凭证标识及预期版本
     * @return 订单履约结果
     */
    FulfillmentResultVO deliver(
            String token, String orderId, String key, FulfillmentDeliverRequest body);

    /**
     * 用户确认送达，复核资金及审核阻塞后完成订单。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param orderId 订单标识
     * @param key 本次操作的幂等键，作用域由现有业务规则决定
     * @param body 调用方持有的订单或任务并发版本
     * @return 履约订单状态
     */
    FulfillmentOrderVO confirm(
            String token, String orderId, String key, FulfillmentVersionRequest body);

    /**
     * 批量检查已到期完成窗口，按相同业务与锁规则推进订单。
     *
     * @return 本批自动完成的订单数量
     */
    int autoComplete();

    /**
     * 在调用方事务中登记完成阻塞，不以仅上传照片作为触发条件。
     *
     * <p>必须与创建调整或售后记录使用同一事务；仅上传照片不触发阻塞。
     *
     * @param orderId 订单标识
     * @param type 本次操作的业务分类
     * @param sourceId 阻塞业务来源记录标识
     */
    void blockCompletion(String orderId, String type, String sourceId);

    /**
     * 在调用方事务中解除指定业务来源的完成阻塞。
     *
     * @param orderId 订单标识
     * @param type 本次操作的业务分类
     * @param sourceId 阻塞业务来源记录标识
     */
    void unblockCompletion(String orderId, String type, String sourceId);

    /**
     * 资金和审核处理完毕后重新开启完整确认窗口。
     *
     * @param orderId 订单标识
     */
    void restartWindow(String orderId);

    /**
     * 在其他订单锁之前校验规格调整写权限及凭证，保持账号、收件人、订单锁顺序。
     *
     * <p>调用方须在获取其他订单锁之前调用，防止与取消路径形成相反锁顺序。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param orderId 订单标识
     * @param taskId 履约任务标识
     * @param expectedVersion 调用方看到的并发版本，更新必须与当前版本一致
     * @param proofId 履约凭证标识
     */
    void authorizeSpecification(
            String token, String orderId, String taskId, long expectedVersion, String proofId);
}
