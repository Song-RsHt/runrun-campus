package com.zhangxuan.runruncampus.courier;

import com.zhangxuan.runruncampus.user.AppUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.time.Instant;

public interface CourierCertificationRepository extends JpaRepository<CourierCertification, Long> {
    Optional<CourierCertification> findByCourier(AppUser courier);
    List<CourierCertification> findByCourierIn(Collection<AppUser> couriers);
    List<CourierCertification> findByStatusOrderBySubmittedAtAsc(CourierCertificationStatus status);
    long countByStatus(CourierCertificationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CourierCertification c where c.status = 'APPROVED' "
            + "and c.approvedPhotoKey is not null and c.reviewedAt < :before")
    List<CourierCertification> findApprovedPhotosToClean(@Param("before") Instant before);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CourierCertification c where c.status = 'REJECTED' "
            + "and c.submittedPhotoKey is not null and c.reviewedAt < :before")
    List<CourierCertification> findRejectedPhotosToClean(@Param("before") Instant before);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CourierCertification c where c.courier = :courier")
    Optional<CourierCertification> findLockedByCourier(@Param("courier") AppUser courier);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CourierCertification c where c.id = :id")
    Optional<CourierCertification> findLockedById(@Param("id") Long id);
}
