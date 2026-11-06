package com.zhangxuan.runruncampus.repository;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.CampusOrder;
import com.zhangxuan.runruncampus.entity.OrderIssue;
import com.zhangxuan.runruncampus.enums.OrderIssueHandlingStatus;
import com.zhangxuan.runruncampus.enums.OrderIssueType;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;



















public interface OrderIssueRepository extends JpaRepository<OrderIssue, Long> {

    List<OrderIssue> findByOrderOrderByCreatedAtAsc(CampusOrder order);

    long countByHandlingStatus(OrderIssueHandlingStatus handlingStatus);

    boolean existsByOrderAndReporterAndTypeAndHandlingStatusIn(CampusOrder order, AppUser reporter, OrderIssueType type, Collection<OrderIssueHandlingStatus> handlingStatuses);

    @Query(value = """
        select issue from OrderIssue issue
        join fetch issue.order orderEntity
        join fetch issue.reporter reporter
        where (:type is null or issue.type = :type)
          and (:handlingStatus is null or issue.handlingStatus = :handlingStatus)
          and (:overdueOnly = false or
            (issue.handlingStatus = com.zhangxuan.runruncampus.enums.OrderIssueHandlingStatus.PENDING
              and issue.createdAt < :pendingCutoff)
            or (issue.handlingStatus = com.zhangxuan.runruncampus.enums.OrderIssueHandlingStatus.PROCESSING
              and issue.updatedAt < :processingCutoff))
          and (:query is null
            or lower(orderEntity.orderNo) like lower(concat('%', :query, '%'))
            or lower(reporter.displayName) like lower(concat('%', :query, '%'))
            or reporter.phone like concat('%', :query, '%')
            or lower(issue.description) like lower(concat('%', :query, '%')))
        """, countQuery = """
        select count(issue) from OrderIssue issue
        join issue.order orderEntity
        join issue.reporter reporter
        where (:type is null or issue.type = :type)
          and (:handlingStatus is null or issue.handlingStatus = :handlingStatus)
          and (:overdueOnly = false or
            (issue.handlingStatus = com.zhangxuan.runruncampus.enums.OrderIssueHandlingStatus.PENDING
              and issue.createdAt < :pendingCutoff)
            or (issue.handlingStatus = com.zhangxuan.runruncampus.enums.OrderIssueHandlingStatus.PROCESSING
              and issue.updatedAt < :processingCutoff))
          and (:query is null
            or lower(orderEntity.orderNo) like lower(concat('%', :query, '%'))
            or lower(reporter.displayName) like lower(concat('%', :query, '%'))
            or reporter.phone like concat('%', :query, '%')
            or lower(issue.description) like lower(concat('%', :query, '%')))
        """)
    Page<OrderIssue> searchForAdmin(@Param("query") String query, @Param("type") OrderIssueType type, @Param("handlingStatus") OrderIssueHandlingStatus handlingStatus, @Param("overdueOnly") boolean overdueOnly, @Param("pendingCutoff") Instant pendingCutoff, @Param("processingCutoff") Instant processingCutoff, Pageable pageable);

    @Query(value = """
        select issue from OrderIssue issue
        join fetch issue.order
        join fetch issue.reporter
        where (:type is null or issue.type = :type)
          and (:handlingStatus is null or issue.handlingStatus = :handlingStatus)
          and (:overdueOnly = false or
            (issue.handlingStatus = com.zhangxuan.runruncampus.enums.OrderIssueHandlingStatus.PENDING
              and issue.createdAt < :pendingCutoff)
            or (issue.handlingStatus = com.zhangxuan.runruncampus.enums.OrderIssueHandlingStatus.PROCESSING
              and issue.updatedAt < :processingCutoff))
        """, countQuery = """
        select count(issue) from OrderIssue issue
        where (:type is null or issue.type = :type)
          and (:handlingStatus is null or issue.handlingStatus = :handlingStatus)
          and (:overdueOnly = false or
            (issue.handlingStatus = com.zhangxuan.runruncampus.enums.OrderIssueHandlingStatus.PENDING
              and issue.createdAt < :pendingCutoff)
            or (issue.handlingStatus = com.zhangxuan.runruncampus.enums.OrderIssueHandlingStatus.PROCESSING
              and issue.updatedAt < :processingCutoff))
        """)
    Page<OrderIssue> filterForAdmin(@Param("type") OrderIssueType type, @Param("handlingStatus") OrderIssueHandlingStatus handlingStatus, @Param("overdueOnly") boolean overdueOnly, @Param("pendingCutoff") Instant pendingCutoff, @Param("processingCutoff") Instant processingCutoff, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select issue from OrderIssue issue join fetch issue.order where issue.id = :id")
    Optional<OrderIssue> findLockedById(@Param("id") Long id);
}
