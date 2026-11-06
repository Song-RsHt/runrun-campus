package com.zhangxuan.runruncampus.repository;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.CourierCertification;
import com.zhangxuan.runruncampus.enums.CourierCertificationStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;















public interface CourierCertificationRepository extends JpaRepository<CourierCertification, Long> {

    Optional<CourierCertification> findByCourier(AppUser courier);

    List<CourierCertification> findByCourierIn(Collection<AppUser> couriers);

    List<CourierCertification> findByStatusOrderBySubmittedAtAsc(CourierCertificationStatus status);

    long countByStatus(CourierCertificationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CourierCertification c where c.status = 'APPROVED' " + "and c.approvedPhotoKey is not null and c.reviewedAt < :before")
    List<CourierCertification> findApprovedPhotosToClean(@Param("before") Instant before);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CourierCertification c where c.status = 'REJECTED' " + "and c.submittedPhotoKey is not null and c.reviewedAt < :before")
    List<CourierCertification> findRejectedPhotosToClean(@Param("before") Instant before);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CourierCertification c where c.courier = :courier")
    Optional<CourierCertification> findLockedByCourier(@Param("courier") AppUser courier);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CourierCertification c where c.id = :id")
    Optional<CourierCertification> findLockedById(@Param("id") Long id);
}
