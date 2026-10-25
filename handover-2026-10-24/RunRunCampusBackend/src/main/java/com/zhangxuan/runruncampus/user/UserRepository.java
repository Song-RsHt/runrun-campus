package com.zhangxuan.runruncampus.user;

import com.zhangxuan.runruncampus.courier.CourierCertificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.List;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByPhone(String phone);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.phone = :phone")
    Optional<AppUser> findLockedByPhone(@Param("phone") String phone);
    long countByRole(UserRole role);
    List<AppUser> findByRoleOrderByCreatedAtAsc(UserRole role);

    @Query(value = """
            select u from AppUser u
            where (:role is null or u.role = :role)
              and (:query is null
                or lower(u.displayName) like lower(concat('%', :query, '%'))
                or u.phone like concat('%', :query, '%'))
            """,
            countQuery = """
            select count(u) from AppUser u
            where (:role is null or u.role = :role)
              and (:query is null
                or lower(u.displayName) like lower(concat('%', :query, '%'))
                or u.phone like concat('%', :query, '%'))
            """)
    Page<AppUser> searchForAdmin(@Param("query") String query, @Param("role") UserRole role, Pageable pageable);

    @Query("select u from AppUser u where (:role is null or u.role = :role)")
    Page<AppUser> filterForAdmin(@Param("role") UserRole role, Pageable pageable);

    @Query(value = """
            select u from AppUser u
            where u.role = com.zhangxuan.runruncampus.user.UserRole.COURIER
              and (:query is null
                or lower(u.displayName) like lower(concat('%', :query, '%'))
                or u.phone like concat('%', :query, '%')
                or exists (select c from CourierCertification c where c.courier = u and (
                    lower(c.realName) like lower(concat('%', :query, '%'))
                    or lower(c.studentId) like lower(concat('%', :query, '%'))
                    or lower(c.campus) like lower(concat('%', :query, '%')))))
              and (:status is null
                or exists (select c from CourierCertification c where c.courier = u and c.status = :status)
                or (:status = com.zhangxuan.runruncampus.courier.CourierCertificationStatus.NOT_APPLIED
                    and u.courierApproved = false
                    and not exists (select c from CourierCertification c where c.courier = u))
                or (:status = com.zhangxuan.runruncampus.courier.CourierCertificationStatus.APPROVED
                    and (u.courierApproved = true or u.courierApproved is null)
                    and not exists (select c from CourierCertification c where c.courier = u)))
            """,
            countQuery = """
            select count(u) from AppUser u
            where u.role = com.zhangxuan.runruncampus.user.UserRole.COURIER
              and (:query is null
                or lower(u.displayName) like lower(concat('%', :query, '%'))
                or u.phone like concat('%', :query, '%')
                or exists (select c from CourierCertification c where c.courier = u and (
                    lower(c.realName) like lower(concat('%', :query, '%'))
                    or lower(c.studentId) like lower(concat('%', :query, '%'))
                    or lower(c.campus) like lower(concat('%', :query, '%')))))
              and (:status is null
                or exists (select c from CourierCertification c where c.courier = u and c.status = :status)
                or (:status = com.zhangxuan.runruncampus.courier.CourierCertificationStatus.NOT_APPLIED
                    and u.courierApproved = false
                    and not exists (select c from CourierCertification c where c.courier = u))
                or (:status = com.zhangxuan.runruncampus.courier.CourierCertificationStatus.APPROVED
                    and (u.courierApproved = true or u.courierApproved is null)
                    and not exists (select c from CourierCertification c where c.courier = u)))
            """)
    Page<AppUser> searchCouriersForAdmin(@Param("query") String query,
                                         @Param("status") CourierCertificationStatus status,
                                         Pageable pageable);

    @Query(value = """
            select u from AppUser u
            where u.role = com.zhangxuan.runruncampus.user.UserRole.COURIER
              and (:status is null
                or exists (select c from CourierCertification c where c.courier = u and c.status = :status)
                or (:status = com.zhangxuan.runruncampus.courier.CourierCertificationStatus.NOT_APPLIED
                    and u.courierApproved = false
                    and not exists (select c from CourierCertification c where c.courier = u))
                or (:status = com.zhangxuan.runruncampus.courier.CourierCertificationStatus.APPROVED
                    and (u.courierApproved = true or u.courierApproved is null)
                    and not exists (select c from CourierCertification c where c.courier = u)))
            """,
            countQuery = """
            select count(u) from AppUser u
            where u.role = com.zhangxuan.runruncampus.user.UserRole.COURIER
              and (:status is null
                or exists (select c from CourierCertification c where c.courier = u and c.status = :status)
                or (:status = com.zhangxuan.runruncampus.courier.CourierCertificationStatus.NOT_APPLIED
                    and u.courierApproved = false
                    and not exists (select c from CourierCertification c where c.courier = u))
                or (:status = com.zhangxuan.runruncampus.courier.CourierCertificationStatus.APPROVED
                    and (u.courierApproved = true or u.courierApproved is null)
                    and not exists (select c from CourierCertification c where c.courier = u)))
            """)
    Page<AppUser> filterCouriersForAdmin(@Param("status") CourierCertificationStatus status,
                                         Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.id = :id")
    Optional<AppUser> findLockedById(@Param("id") Long id);
}
