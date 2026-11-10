package com.zhangxuan.runrunv2.mapper.evidence;

import com.zhangxuan.runrunv2.config.SingleRowRequired;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 证据保全与删除持久化映射；事务、锁顺序及业务校验由原调用方保持。 */
@Mapper
public interface CourierEvidenceGovernanceMapper {

    @SingleRowRequired
    Long countOrderById(@Param("orderId") Long orderId);

    @SingleRowRequired
    Long countCertificationById(@Param("certificationId") Long certificationId);

    @SingleRowRequired
    Long insertEvidenceHold(
            @Param("orderId") Long orderId,
            @Param("certificationId") Long certificationId,
            @Param("reason") String reason,
            @Param("ownerId") long ownerId,
            @Param("reviewDueAt") OffsetDateTime reviewDueAt);

    int insertHoldCreatedAudit(
            @Param("administratorId") long administratorId,
            @Param("holdId") long holdId,
            @Param("requestId") String requestId,
            @Param("reason") String reason,
            @Param("orderIdParameter") Long orderIdParameter,
            @Param("certificationIdParameter") Long certificationIdParameter);

    /** 读取并锁定 runrun_v2.evidence_holds 的匹配行；锁持有至调用方事务结束。 */
    List<Map<String, Object>> lockEvidenceHold(@Param("holdId") long holdId);

    int releaseEvidenceHold(@Param("holdId") long holdId);

    int insertHoldReleasedAudit(
            @Param("administratorId") long administratorId,
            @Param("targetId") long targetId,
            @Param("requestId") String requestId,
            @Param("reason") String reason);

    @SingleRowRequired
    Long countActiveCertificationHolds(@Param("certificationId") long certificationId);

    int insertDeletionRequestIfAbsent(
            @Param("certificationId") long certificationId,
            @Param("resourceId") String resourceId,
            @Param("storageKey") String storageKey,
            @Param("deleteAfter") OffsetDateTime deleteAfter,
            @Param("existingCertificationId") long existingCertificationId,
            @Param("existingResourceId") String existingResourceId);

    /** 读取并锁定 runrun_v2.evidence_deletion_audits 的匹配行；锁持有至调用方事务结束。跳过其他事务已锁定的候选，保持原领取顺序与批次条件。 */
    List<Map<String, Object>> lockDueDeletions();

    List<Map<String, Object>> findExpiredApprovedPhotos();

    @SingleRowRequired
    Long countActiveHoldsBeforeDeletion(@Param("certificationId") long certificationId);

    int markDeletionInProgress(@Param("deletionAuditId") long deletionAuditId);

    int clearSubmittedPhoto(
            @Param("certificationId") long certificationId, @Param("resourceId") String resourceId);

    int clearApprovedPhoto(
            @Param("certificationId") long certificationId, @Param("resourceId") String resourceId);

    int markDeletionSucceeded(@Param("deletionAuditId") long deletionAuditId);

    int markDeletionFailed(
            @Param("errorCode") String errorCode, @Param("deletionAuditId") long deletionAuditId);

    /** 读取并锁定 runrun_v2.v2_orders 的匹配行；锁持有至调用方事务结束。 */
    @SingleRowRequired
    Long lockOrder(@Param("orderId") Long orderId);

    /** 读取并锁定 runrun_v2.courier_certifications 的匹配行；锁持有至调用方事务结束。 */
    @SingleRowRequired
    Long lockCertification(@Param("certificationId") Long certificationId);
}
