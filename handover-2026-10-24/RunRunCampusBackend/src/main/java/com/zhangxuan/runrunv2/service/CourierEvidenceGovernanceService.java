package com.zhangxuan.runrunv2.service;

import com.zhangxuan.runrunv2.vo.EvidenceHoldResultVO;

import java.time.Instant;

/** 新系统证据保全与删除业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface CourierEvidenceGovernanceService {

    /**
     * 登记订单或资质证据的保全及期限，保全优先于删除。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param orderId 订单标识
     * @param certificationId 骑手资质标识
     * @param reason 业务操作理由，按原有长度和必填规则校验
     * @param due 证据保全截止时刻
     * @param requestId 本次操作的幂等请求标识
     * @return 证据保全结果
     */
    EvidenceHoldResultVO hold(
            String token,
            Long orderId,
            Long certificationId,
            String reason,
            Instant due,
            String requestId);

    /**
     * 释放指定证据保全，并恢复原有保留期及删除资格判断。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param holdId 证据保全标识
     * @param requestId 本次操作的幂等请求标识
     * @return 证据保全结果
     */
    EvidenceHoldResultVO release(String token, long holdId, String requestId);

    /**
     * 为指定资质图片登记延迟删除任务，不立即删除对象。
     *
     * @param certificationId 骑手资质标识
     * @param resourceId 私有图片资源标识
     * @param storageKey 私有对象存储键，仅在服务内部使用
     * @param deleteAfter 按保留策略计算的最早删除时刻
     */
    void requestCertificationDeletion(
            long certificationId, String resourceId, String storageKey, Instant deleteAfter);

    /**
     * 处理到期保全与删除任务，失败按原重试策略保留。
     *
     * <p>保全优先于删除；删除失败保留原重试记录，不能丢弃审计事实。
     */
    void cleanupDue();
}
