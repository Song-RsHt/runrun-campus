package com.zhangxuan.runrunv2.mapper.courier;

import com.zhangxuan.runrunv2.config.SingleRowRequired;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** 资质审核持久化映射；事务、锁顺序及业务校验由原调用方保持。 */
@Mapper
public interface CourierCertificationV2Mapper {

    /** 读取并锁定 runrun_v2.courier_certifications 的匹配行；锁持有至调用方事务结束。 */
    List<Map<String, Object>> lockCourierCertification(@Param("courierId") long courierId);

    int insertPendingCertification(@Param("courierId") long courierId);

    int resubmitCertification(@Param("courierId") long courierId);

    @SingleRowRequired
    Long findCertificationId(@Param("courierId") long courierId);

    /** 按标识和原有版本条件更新 runrun_v2.courier_certifications；返回影响行数，由业务层判定并发冲突。 */
    int updateSubmittedPhoto(
            @Param("resourceId") String resourceId,
            @Param("storageKey") String storageKey,
            @Param("certificationId") long certificationId,
            @Param("expectedVersion") long expectedVersion);

    /** 按标识和原有版本条件更新 runrun_v2.courier_certifications；返回影响行数，由业务层判定并发冲突。 */
    int touchCertificationIfVersionMatches(
            @Param("certificationId") long certificationId,
            @Param("expectedVersion") long expectedVersion);

    /** 按标识和原有版本条件更新 runrun_v2.courier_certifications；返回影响行数，由业务层判定并发冲突。 */
    int approveCertification(
            @Param("certificationId") long certificationId,
            @Param("expectedVersion") long expectedVersion);

    /** 按标识和原有版本条件更新 runrun_v2.courier_certifications；返回影响行数，由业务层判定并发冲突。 */
    int rejectCertification(
            @Param("reason") String reason,
            @Param("certificationId") long certificationId,
            @Param("expectedVersion") long expectedVersion);

    @SingleRowRequired
    Long countPhotoDeletionsInProgress(
            @Param("certificationId") long certificationId, @Param("resourceId") String resourceId);

    int insertPhotoReadAudit(
            @Param("administratorId") long administratorId,
            @Param("certificationId") long certificationId,
            @Param("requestId") String requestId,
            @Param("reason") String reason,
            @Param("resourceId") String resourceId);

    @SingleRowRequired
    Long countCertifications(
            @Param("statusFilter") String statusFilter,
            @Param("status") String status,
            @Param("phoneFilter") String phoneFilter,
            @Param("phonePattern") String phonePattern);

    List<Map<String, Object>> pageCertifications(
            @Param("statusFilter") String statusFilter,
            @Param("status") String status,
            @Param("phoneFilter") String phoneFilter,
            @Param("phonePattern") String phonePattern,
            @Param("limit") int limit,
            @Param("offset") long offset);

    @SingleRowRequired
    Long countDeliveredTasks(@Param("courierId") long courierId);

    @SingleRowRequired
    Map<String, Object> summarizeDeliveredTasks(@Param("courierId") long courierId);

    List<Map<String, Object>> pageDeliveredTasks(
            @Param("courierId") long courierId,
            @Param("limit") int limit,
            @Param("offset") long offset);

    List<Map<String, Object>> findCourierCertification(@Param("courierId") long courierId);

    /** 读取并锁定 runrun_v2.courier_certifications 的匹配行；锁持有至调用方事务结束。 */
    List<Map<String, Object>> lockCertification(@Param("certificationId") long certificationId);

    /** 读取并锁定 runrun_v2.app_users 的匹配行；锁持有至调用方事务结束。 */
    @SingleRowRequired
    Long lockActor(@Param("actorId") long actorId);

    int insertCertificationEvent(
            @Param("certificationId") long certificationId,
            @Param("actorId") long actorId,
            @Param("actorKind") String actorKind,
            @Param("eventType") String eventType,
            @Param("requestId") String requestId,
            @Param("versionSourceCertificationId") long versionSourceCertificationId);
}
