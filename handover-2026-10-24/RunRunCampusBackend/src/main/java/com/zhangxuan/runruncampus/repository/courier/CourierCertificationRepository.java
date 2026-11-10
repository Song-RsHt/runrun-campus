package com.zhangxuan.runruncampus.repository.courier;

import com.zhangxuan.runruncampus.entity.courier.CourierCertification;
import com.zhangxuan.runruncampus.entity.user.AppUser;
import com.zhangxuan.runruncampus.enums.courier.CourierCertificationStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 旧系统骑手资质 JPA 仓储；只定义持久化查询和写入，不承担权限及业务决策。 */
public interface CourierCertificationRepository extends JpaRepository<CourierCertification, Long> {

    Optional<CourierCertification> findByCourier(AppUser courier);

    List<CourierCertification> findByCourierIn(Collection<AppUser> couriers);

    List<CourierCertification> findByStatusOrderBySubmittedAtAsc(CourierCertificationStatus status);

    long countByStatus(CourierCertificationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select c from CourierCertification c where c.status = 'APPROVED' "
                    + "and c.approvedPhotoKey is not null and c.reviewedAt < :before")
    List<CourierCertification> findApprovedPhotosToClean(@Param("before") Instant before);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select c from CourierCertification c where c.status = 'REJECTED' "
                    + "and c.submittedPhotoKey is not null and c.reviewedAt < :before")
    List<CourierCertification> findRejectedPhotosToClean(@Param("before") Instant before);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CourierCertification c where c.courier = :courier")
    Optional<CourierCertification> findLockedByCourier(@Param("courier") AppUser courier);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CourierCertification c where c.id = :id")
    Optional<CourierCertification> findLockedById(@Param("id") Long id);
}
